package battlecode.server;

import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Direction;
import battlecode.common.UnitType;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.instrumenter.profiler.Profiler;
import battlecode.instrumenter.profiler.ProfilerCollection;
import battlecode.instrumenter.profiler.ProfilerEventType;
import battlecode.schema.*;
import battlecode.util.FlatHelpers;
import battlecode.util.TeamMapping;
import battlecode.world.*;
import com.google.flatbuffers.FlatBufferBuilder;
import gnu.trove.TIntArrayList;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.function.ToIntFunction;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a game to a flatbuffer, hooray.
 */
public class GameMaker {

    /**
     * The protocol expects a series of valid state transitions;
     * we ensure that's true.
     */
    private enum State {
        /**
         * Waiting to write game header.
         */
        GAME_HEADER,
        /**
         * In a game, but not a match.
         */
        IN_GAME,
        /**
         * In a match.
         */
        IN_MATCH,
        /**
         * Complete.
         */
        DONE
    }

    private State state;

    // this un-separation-of-concerns makes me uncomfortable

    /**
     * We write the whole match to this builder, then write it to a file.
     */
    private final FlatBufferBuilderWrapper fileBuilder;

    /**
     * Null until the end of the match.
     */
    private byte[] finishedGame;

    /**
     * We have a separate byte[] for each packet sent to the client.
     * This is necessary because flatbuffers shares metadata between structures, so
     * we
     * can't just cut out chunks of the larger buffer :/
     */
    private FlatBufferBuilderWrapper packetBuilder;

    /**
     * The server we're sending packets on.
     * May be null.
     */
    private final NetServer packetSink;

    /**
     * Information about the active game.
     */
    private final GameInfo gameInfo;

    /**
     * Only relevant to the file builder:
     * We add a table called a GameWrapper to the front of the saved files
     * that lets you quickly navigate to events by index, and tells you the
     * indices of headers and footers.
     */
    private TIntArrayList events;
    private TIntArrayList matchHeaders;
    private TIntArrayList matchFooters;

    /**
     * The MatchMaker associated with this GameMaker.
     */
    private final MatchMaker matchMaker;

    /**
     * Whether to serialize indicator dots and lines into the flatbuffer.
     */
    private final boolean showIndicators;

    /**
     * @param gameInfo       the mapping of teams to bytes
     * @param packetSink     the NetServer to send packets to
     * @param showIndicators whether to write indicator dots and lines to replay
     */
    public GameMaker(final GameInfo gameInfo, final NetServer packetSink, final boolean showIndicators) {
        this.state = State.GAME_HEADER;

        this.gameInfo = gameInfo;

        this.packetSink = packetSink;
        if (packetSink != null) {
            this.packetBuilder = new FlatBufferBuilderWrapper();
        }

        this.fileBuilder = new FlatBufferBuilderWrapper();

        this.events = new TIntArrayList();
        this.matchHeaders = new TIntArrayList();
        this.matchFooters = new TIntArrayList();

        this.matchMaker = new MatchMaker();

        this.showIndicators = showIndicators;
    }

    /**
     * Assert we're in a particular state.
     *
     * @param state
     */
    private void assertState(State state) {
        if (this.state != state) {
            throw new RuntimeException("Incorrect GameMaker state: should be " +
                    state + ", but is: " + this.state);
        }
    }

    /**
     * Make a state transition.
     */
    private void changeState(State start, State end) {
        assertState(start);
        this.state = end;
    }

    /**
     * Convert entire game to a byte array.
     *
     * @return game as a packed flatbuffer byte array.
     */
    public byte[] toBytes() {
        if (finishedGame == null) {
            assertState(State.DONE);

            int events = GameWrapper.createEventsVector(fileBuilder, this.events.toNativeArray());
            int matchHeaders = GameWrapper.createMatchHeadersVector(fileBuilder, this.matchHeaders.toNativeArray());
            int matchFooters = GameWrapper.createMatchFootersVector(fileBuilder, this.matchFooters.toNativeArray());

            GameWrapper.startGameWrapper(fileBuilder);
            GameWrapper.addEvents(fileBuilder, events);
            GameWrapper.addMatchHeaders(fileBuilder, matchHeaders);
            GameWrapper.addMatchFooters(fileBuilder, matchFooters);

            fileBuilder.finish(GameWrapper.endGameWrapper(fileBuilder));
            byte[] rawBytes = fileBuilder.sizedByteArray();

            try {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                GZIPOutputStream zipper = new GZIPOutputStream(result);
                IOUtils.copy(new ByteArrayInputStream(rawBytes), zipper);
                zipper.close();
                zipper.flush();
                result.flush();
                finishedGame = result.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Gzipping failed?", e);
            }
        }
        return finishedGame;
    }

    /**
     * Write a match out to a file.
     * 
     * @param saveFile the file to save to
     */

    public void writeGame(File saveFile) {
        if (saveFile == null) {
            throw new RuntimeException("Null file provided to writeGame");
        }

        try {
            FileUtils.writeByteArrayToFile(saveFile, toBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Run the same logic for both builders.
     *
     * @param perBuilder called with each builder;
     */
    private void applyToBuilders(Consumer<FlatBufferBuilderWrapper> perBuilder) {
        perBuilder.accept(fileBuilder);
        if (packetSink != null) {
            perBuilder.accept(packetBuilder);
        }
    }

    /**
     * Run the same logic for both builders.
     *
     * @param perBuilder called with each builder; return event id. Should not
     *                   mutate state.
     */
    private void createEvent(ToIntFunction<FlatBufferBuilderWrapper> perBuilder) {
        // make file event and add its offset to the list
        int eventAP = perBuilder.applyAsInt(fileBuilder);
        events.add(eventAP);

        if (packetSink != null) {
            // make packet event and package it up
            int eventBP = perBuilder.applyAsInt(packetBuilder);
            packetBuilder.finish(eventBP);
            packetSink.addEvent(packetBuilder.sizedByteArray());

            // reset packet builder
            packetBuilder = new FlatBufferBuilderWrapper(packetBuilder.dataBuffer());
        }
    }

    /**
     * Get the MatchMaker associated with this GameMaker.
     */
    public MatchMaker getMatchMaker() {
        return this.matchMaker;
    }

    public void makeGameHeader() {

        changeState(State.GAME_HEADER, State.IN_GAME);

        createEvent((builder) -> {
            int specVersionOffset = builder.createString(GameConstants.SPEC_VERSION);

            int name = builder.createString(gameInfo.getTeamAName());
            int packageName = builder.createString(gameInfo.getTeamAPackage());
            TeamData.startTeamData(builder);
            TeamData.addName(builder, name);
            TeamData.addPackageName(builder, packageName);
            TeamData.addTeamId(builder, TeamMapping.id(Team.A));

            int teamAOffset = TeamData.endTeamData(builder);

            name = builder.createString(gameInfo.getTeamBName());
            packageName = builder.createString(gameInfo.getTeamBPackage());
            TeamData.startTeamData(builder);
            TeamData.addName(builder, name);
            TeamData.addPackageName(builder, packageName);
            TeamData.addTeamId(builder, TeamMapping.id(Team.B));
            int teamBOffset = TeamData.endTeamData(builder);

            int[] teamsVec = {teamAOffset, teamBOffset};

            int teamsOffset = GameHeader.createTeamsVector(builder, teamsVec);
            int robotTypeMetaDataOffset = makeRobotTypeMetadata(builder);

            GameplayConstants.startGameplayConstants(builder);
            int constantsOffset = GameplayConstants.endGameplayConstants(builder);

            GameHeader.startGameHeader(builder);
            GameHeader.addSpecVersion(builder, specVersionOffset);
            GameHeader.addTeams(builder, teamsOffset);
            GameHeader.addConstants(builder, constantsOffset);
            GameHeader.addRobotTypeMetadata(builder, robotTypeMetaDataOffset);
            int gameHeaderOffset = GameHeader.endGameHeader(builder);

            return EventWrapper.createEventWrapper(builder, Event.GameHeader, gameHeaderOffset);
        });
    }

    public int makeRobotTypeMetadata(FlatBufferBuilder builder) {
        TIntArrayList robotTypeMetadataOffsets = new TIntArrayList();
        for (UnitType type : UnitType.values()) {
            // turns all types into level 1 to convert easily into RobotType
            UnitType levelOneType = FlatHelpers.getUnitTypeFromRobotType(FlatHelpers.getRobotTypeFromUnitType(type));

            if (type != levelOneType) {
                continue; // avoid double counting
            }

            RobotTypeMetadata.startRobotTypeMetadata(builder);
            RobotTypeMetadata.addType(builder, FlatHelpers.getRobotTypeFromUnitType(type));
            RobotTypeMetadata.addActionCooldown(builder, type.actionCooldown);
            RobotTypeMetadata.addTurningCooldown(builder, GameConstants.TURNING_COOLDOWN);
            RobotTypeMetadata.addBaseHealth(builder, type.health);
            RobotTypeMetadata.addBytecodeLimit(builder, type.bytecodeLimit);
            RobotTypeMetadata.addMovementCooldown(builder, type.movementCooldown);
            RobotTypeMetadata.addVisionConeRadiusSquared(builder, type.visionConeRadiusSquared);
            RobotTypeMetadata.addVisionConeAngle(builder, type.visionConeAngle);
            RobotTypeMetadata.addMessageRadiusSquared(builder, GameConstants.SQUEAK_RADIUS_SQUARED);
            robotTypeMetadataOffsets.add(RobotTypeMetadata.endRobotTypeMetadata(builder));
        }
        return GameHeader.createRobotTypeMetadataVector(builder, robotTypeMetadataOffsets.toNativeArray());
    }

    public void makeGameFooter(Team winner) {
        changeState(State.IN_GAME, State.DONE);

        createEvent((builder) -> EventWrapper.createEventWrapper(builder, Event.GameFooter,
                GameFooter.createGameFooter(builder, TeamMapping.id(winner))));
    }

    /**
     * Writes events from match to one or multiple flatbuffers.
     *
     * One of the rare cases where we want a non-static inner class in Java:
     * this basically just provides a restricted interface to GameMaker.
     *
     * There is only one of these per GameMaker.
     */
    public class MatchMaker {

        // Round statistics
        private TIntArrayList teamIDs;
        private TIntArrayList teamCatDamage;
        private TIntArrayList teamCheeseTransferred;
        private TIntArrayList teamAliveRatKings;
        private TIntArrayList teamAliveBabyRats;
        private TIntArrayList teamRatTrapCount;
        private TIntArrayList teamCatTrapCount;
        private TIntArrayList teamDirtCount;

        private TIntArrayList diedIds; // ints

        // private TIntArrayList trapAddedIds;
        // private TIntArrayList trapAddedX;
        // private TIntArrayList trapAddedY;
        // private TByteArrayList trapAddedTypes;
        // private TByteArrayList trapAddedTeams;

        // private TIntArrayList trapTriggeredIds;

        private int currentRound;
        private int currentMapWidth = -1;

        private ArrayList<Byte> timelineMarkerTeams;
        private ArrayList<Integer> timelineMarkerRounds;
        private ArrayList<String> timelineMarkerLabels;
        private ArrayList<Integer> timelineMarkerColors;

        // Used to write logs.
        private final ByteArrayOutputStream logger;

        public MatchMaker() {
            this.teamIDs = new TIntArrayList();
            this.teamCatDamage = new TIntArrayList();
            this.teamCheeseTransferred = new TIntArrayList();
            this.teamAliveRatKings = new TIntArrayList();
            this.teamAliveBabyRats = new TIntArrayList();
            this.teamDirtCount = new TIntArrayList();
            this.teamRatTrapCount = new TIntArrayList();
            this.teamCatTrapCount = new TIntArrayList();

            this.diedIds = new TIntArrayList();
            this.currentRound = 0;
            this.logger = new ByteArrayOutputStream();
            this.timelineMarkerTeams = new ArrayList<>();
            this.timelineMarkerRounds = new ArrayList<>();
            this.timelineMarkerLabels = new ArrayList<>();
            this.timelineMarkerColors = new ArrayList<>();
            
            // this.trapAddedIds = new TIntArrayList();
            // this.trapAddedX = new TIntArrayList();
            // this.trapAddedY = new TIntArrayList();
            // this.trapAddedTypes = new TByteArrayList();
            // this.trapAddedTeams = new TByteArrayList();
            // this.trapTriggeredIds = new TIntArrayList();

        }

        public void makeMatchHeader(LiveMap gameMap) {
            changeState(State.IN_GAME, State.IN_MATCH);
            this.currentMapWidth = gameMap.getWidth();
            createEvent((builder) -> {
                int map = GameMapIO.Serial.serialize(builder, gameMap);
                return EventWrapper.createEventWrapper(builder, Event.MatchHeader,
                        MatchHeader.createMatchHeader(builder, map, gameMap.getRounds()));
            });

            matchHeaders.add(events.size() - 1);

            clearMatchData();
        }

        public void makeMatchFooter(Team winTeam, DominationFactor winType, int totalRounds,
                List<ProfilerCollection> profilerCollections) {
            changeState(State.IN_MATCH, State.IN_GAME);

            createEvent((builder) -> {
                TIntArrayList profilerFiles = new TIntArrayList();

                for (ProfilerCollection profilerCollection : profilerCollections) {
                    TIntArrayList frames = new TIntArrayList();
                    TIntArrayList profiles = new TIntArrayList();

                    for (String frame : profilerCollection.getFrames()) {
                        frames.add(builder.createString(frame));
                    }

                    for (Profiler profiler : profilerCollection.getProfilers()) {
                        TIntArrayList events = new TIntArrayList();

                        for (battlecode.instrumenter.profiler.ProfilerEvent event : profiler.getEvents()) {
                            ProfilerEvent.startProfilerEvent(builder);
                            ProfilerEvent.addIsOpen(builder, event.getType() == ProfilerEventType.OPEN);
                            ProfilerEvent.addAt(builder, event.getAt());
                            ProfilerEvent.addFrame(builder, event.getFrameId());
                            events.add(ProfilerEvent.endProfilerEvent(builder));
                        }

                        int nameOffset = builder.createString(profiler.getName());
                        int eventsOffset = ProfilerProfile.createEventsVector(builder, events.toNativeArray());

                        ProfilerProfile.startProfilerProfile(builder);
                        ProfilerProfile.addName(builder, nameOffset);
                        ProfilerProfile.addEvents(builder, eventsOffset);
                        profiles.add(ProfilerProfile.endProfilerProfile(builder));
                    }

                    int framesOffset = ProfilerFile.createFramesVector(builder, frames.toNativeArray());
                    int profilesOffset = ProfilerFile.createProfilesVector(builder, profiles.toNativeArray());

                    profilerFiles.add(ProfilerFile.createProfilerFile(builder, framesOffset, profilesOffset));
                }

                int profilerFilesOffset = MatchFooter.createProfilerFilesVector(builder, profilerFiles.toNativeArray());

                TIntArrayList timelineMarkerOffsets = new TIntArrayList();
                for (int i = 0; i < this.timelineMarkerRounds.size(); i++) {
                    int timelineMarkerOffset = TimelineMarker.createTimelineMarker(builder, timelineMarkerTeams.get(i),
                            timelineMarkerRounds.get(i),
                            timelineMarkerColors.get(i), builder.createString(timelineMarkerLabels.get(i)));
                    timelineMarkerOffsets.add(timelineMarkerOffset);
                }
                int timelineMarkersOffset = MatchFooter.createTimelineMarkersVector(builder,
                        timelineMarkerOffsets.toNativeArray());

                return EventWrapper.createEventWrapper(builder, Event.MatchFooter,
                        MatchFooter.createMatchFooter(builder, TeamMapping.id(winTeam),
                                FlatHelpers.getWinTypeFromDominationFactor(winType), totalRounds, timelineMarkersOffset,
                                profilerFilesOffset));
            });

            matchFooters.add(events.size() - 1);
        }

        public void startRound(int roundNum) {
            assertState(State.IN_MATCH);

            try {
                this.logger.flush();
            } catch (IOException e) {
                throw new RuntimeException("Can't flush byte[]outputstream?", e);
            }
            // byte[] logs = this.logger.toByteArray();
            this.logger.reset();
            this.currentRound = roundNum;
        }

        public void endRound() {
            createEvent((builder) -> {
                // Round statistics
                int teamIDsP = Round.createTeamIdsVector(builder, teamIDs.toNativeArray());
                int teamCheeseTransferredP = Round.createTeamCheeseTransferredVector(builder,
                        teamCheeseTransferred.toNativeArray());
                int teamCatDamageP = Round.createTeamCatDamageVector(builder, teamCatDamage.toNativeArray());
                int teamAliveRatKingsP = Round.createTeamAliveRatKingsVector(builder, teamAliveRatKings.toNativeArray());
                int teamAliveBabyRatsP = Round.createTeamAliveBabyRatsVector(builder, teamAliveBabyRats.toNativeArray());
                int teamDirtCountP = Round.createTeamDirtAmountsVector(builder, teamDirtCount.toNativeArray());
                int teamRatTrapCountP = Round.createTeamRatTrapCountVector(builder, teamRatTrapCount.toNativeArray());
                int teamCatTrapCountP = Round.createTeamCatTrapCountVector(builder, teamCatTrapCount.toNativeArray());

                int diedIdsP = Round.createDiedIdsVector(builder, diedIds.toNativeArray());

                builder.startRound();

                Round.addTeamIds(builder, teamIDsP);
                Round.addRoundId(builder, this.currentRound);
                Round.addTeamCheeseTransferred(builder, teamCheeseTransferredP);
                Round.addTeamCatDamage(builder, teamCatDamageP);
                Round.addTeamAliveBabyRats(builder, teamAliveBabyRatsP);
                Round.addTeamAliveRatKings(builder, teamAliveRatKingsP);
                Round.addTeamDirtAmounts(builder, teamDirtCountP);
                Round.addTeamRatTrapCount(builder, teamRatTrapCountP);
                Round.addTeamCatTrapCount(builder, teamCatTrapCountP);
                Round.addDiedIds(builder, diedIdsP);

                int round = builder.finishRound();
                return EventWrapper.createEventWrapper(builder, Event.Round, round);
            });

            clearRoundData();
        }

        public void startTurn(int robotID) {
            return;
        }

        public void endTurn(int robotID, int health, int cheese, int movementCooldown, int actionCooldown, int turningCooldown,
                int bytecodesUsed, MapLocation loc, Direction dir, boolean isCooperation) {
            applyToBuilders((builder) -> {
                builder.startTurn();

                Turn.addRobotId(builder, robotID);
                Turn.addHealth(builder, health);
                Turn.addCheese(builder, cheese);
                Turn.addIsCooperation(builder, isCooperation);
                Turn.addMoveCooldown(builder, movementCooldown);
                Turn.addActionCooldown(builder, actionCooldown);
                Turn.addTurningCooldown(builder, turningCooldown);
                Turn.addBytecodesUsed(builder, bytecodesUsed);
                Turn.addX(builder, loc.x);
                Turn.addY(builder, loc.y);
                Turn.addDir(builder, FlatHelpers.getOrdinalFromDirection(dir));

                builder.finishTurn();
            });
        }

        /**
         * @return an outputstream that will be baked into the output file
         */
        public OutputStream getOut() {
            return logger;
        }

        /// Generic action representing damage to a robot
        public void addDamageAction(int damagedRobotID, int damage) {
            applyToBuilders((builder) -> {
                int action = DamageAction.createDamageAction(builder, damagedRobotID, damage);
                builder.addAction(action, Action.DamageAction);
            });
        }

        public void addRatNapAction(int nappedID) {
            applyToBuilders((builder) -> {
                int action = RatNap.createRatNap(builder, nappedID);
                builder.addAction(action, Action.RatNap);
            });
        }

        public void addCatFeedAction(int sacrificedRatID){
            applyToBuilders((builder) -> {
                int action = CatFeed.createCatFeed(builder, sacrificedRatID);
                builder.addAction(action, Action.CatFeed);
            });
        }


        public void addThrowAction(int thrownRobotID, MapLocation throwDirLocation) {
            applyToBuilders((builder) -> {
                int action = ThrowRat.createThrowRat(builder, thrownRobotID, locationToInt(throwDirLocation));
                builder.addAction(action, Action.ThrowRat);
            });
        }

        /// Visually indicate an rat bite
        public void addBiteAction(int biterID) {
            applyToBuilders((builder) -> {
                int action = RatAttack.createRatAttack(builder, biterID);
                builder.addAction(action, Action.RatAttack);
            });
        }

        /// Visually indicate an cat scratch
        public void addScratchAction(int loc) {
            applyToBuilders((builder) -> {
                int action = CatScratch.createCatScratch(builder, loc);
                builder.addAction(action, Action.CatScratch);
            });
        }

        public void addStunAction(int robotID, int cooldown) {
            applyToBuilders((builder) -> {
                int action = StunAction.createStunAction(builder, robotID, cooldown);
                builder.addAction(action, Action.StunAction);
            });
        }

        public void addBecomeRatKingAction(int id) {
            applyToBuilders((builder) -> {
                int action = UpgradeToRatKing.createUpgradeToRatKing(builder, id);
                builder.addAction(action, Action.UpgradeToRatKing);
            });
        }

        public void addPlaceTrapAction(int trapID, MapLocation loc, Team team, TrapType type) {
            applyToBuilders((builder) -> {
                byte teamID = TeamMapping.id(team);
                int action = PlaceTrap.createPlaceTrap(builder, locationToInt(loc), teamID, type==TrapType.RAT_TRAP);
                builder.addAction(action, Action.PlaceTrap);
            });
        }

        public void addRemoveTrapAction(MapLocation loc, Team team) {
            applyToBuilders((builder) -> {
                byte teamID = TeamMapping.id(team);
                int action = RemoveTrap.createRemoveTrap(builder, locationToInt(loc), teamID);
                builder.addAction(action, Action.RemoveTrap);
            });
        }

        public void addTrapTriggerAction(int trapID, MapLocation loc, Team team, TrapType type) {
            applyToBuilders((builder) -> {
                byte teamID = TeamMapping.id(team);
                int action = TriggerTrap.createTriggerTrap(builder, locationToInt(loc), teamID);
                builder.addAction(action, Action.TriggerTrap);
            });
        }

        /// Visually indicate dirt or trap being built
        public void addPlaceDirtAction(MapLocation loc) {
            applyToBuilders((builder) -> {
                int action = PlaceDirt.createPlaceDirt(builder, locationToInt(loc));
                builder.addAction(action, Action.PlaceDirt);
            });
        }

        /// Visually indicate dirt being removed
        public void addRemoveDirtAction(MapLocation loc) {
            applyToBuilders((builder) -> {
                int action = BreakDirt.createBreakDirt(builder, locationToInt(loc));
                builder.addAction(action, Action.BreakDirt);
            });
        }

        /// Visually indicates a rat squeaking
        public void addSqueakAction(MapLocation loc) {
            applyToBuilders((builder) -> {
                int action = RatSqueak.createRatSqueak(builder, locationToInt(loc));
                builder.addAction(action, Action.RatSqueak);
            });
        }

        /// Visually indicates a cheese transfer
        public void addCheeseTransferAction(int toID, int amount) {
            applyToBuilders((builder) -> {
                int action = CheeseTransfer.createCheeseTransfer(builder, toID, amount);
                builder.addAction(action, Action.CheeseTransfer);
            });
        }

        public void addCheeseSpawnAction(MapLocation loc, int amount) {
            applyToBuilders((builder) -> {
                int action = CheeseSpawn.createCheeseSpawn(builder, locationToInt(loc), amount);
                builder.addAction(action, Action.CheeseSpawn);
            });
        }

        public void addCheesePickUpAction(MapLocation loc) {
            applyToBuilders((builder) -> {
                int action = CheesePickup.createCheesePickup(builder, locationToInt(loc));
                builder.addAction(action, Action.CheesePickup);
            });
        }

        /// Indicate that this robot was spawned on this turn
        public void addSpawnAction(int id, MapLocation loc, Direction dir, int chirality, Team team, UnitType type) {
            applyToBuilders((builder) -> {
                byte teamID = TeamMapping.id(team);
                byte robotType = FlatHelpers.getRobotTypeFromUnitType(type);
                int dirOrdinal = FlatHelpers.getOrdinalFromDirection(dir);
                int action = SpawnAction.createSpawnAction(builder, id, loc.x, loc.y, dirOrdinal, chirality, teamID, robotType);
                builder.addAction(action, Action.SpawnAction);
            });
        }

        public void addDieAction(int id, boolean fromException) {
            byte deathReason = fromException ? DieType.EXCEPTION : DieType.UNKNOWN;
            applyToBuilders((builder) -> {
                int action = DieAction.createDieAction(builder, id, deathReason);
                builder.addAction(action, Action.DieAction);
            });
        }

        public void addTeamInfo(Team team, int cheeseTransferred, int catDamage, int aliveRatKings, int aliveBabyRats, int amountDirtCollected, int ratTrapCount, int catTrapCount) {
            teamIDs.add(TeamMapping.id(team));
            teamCheeseTransferred.add(cheeseTransferred);
            teamCatDamage.add(catDamage);
            teamAliveRatKings.add(aliveRatKings);
            teamAliveBabyRats.add(aliveBabyRats);
            teamDirtCount.add(amountDirtCollected);
            teamRatTrapCount.add(ratTrapCount);
            teamCatTrapCount.add(catTrapCount);
        }

        public void addTimelineMarker(Team team, String label, int red, int green, int blue) {
            if (!showIndicators) {
                return;
            }
            this.timelineMarkerTeams.add((byte) team.ordinal());
            this.timelineMarkerRounds.add(this.currentRound);
            this.timelineMarkerLabels.add(label);
            int color = FlatHelpers.RGBtoInt(red, green, blue);
            this.timelineMarkerColors.add(color);
        }

        /// Update the indicator string for this robot
        public void addIndicatorString(int id, String string) {
            if (!showIndicators) {
                return;
            }
            applyToBuilders((builder) -> {
                int action = IndicatorStringAction.createIndicatorStringAction(builder, builder.createString(string));
                builder.addAction(action, Action.IndicatorStringAction);
            });
        }

        /// Update the indicator dot for this robot
        public void addIndicatorDot(int id, MapLocation loc, int red, int green, int blue) {
            if (!showIndicators) {
                return;
            }
            applyToBuilders((builder) -> {
                int action = IndicatorDotAction.createIndicatorDotAction(builder, locationToInt(loc),
                        FlatHelpers.RGBtoInt(red, green, blue));
                builder.addAction(action, Action.IndicatorDotAction);
            });
        }

        /// Update the indicator line for this robot
        public void addIndicatorLine(int id, MapLocation startLoc, MapLocation endLoc, int red, int green, int blue) {
            if (!showIndicators) {
                return;
            }
            applyToBuilders((builder) -> {
                int action = IndicatorLineAction.createIndicatorLineAction(builder, locationToInt(startLoc),
                        locationToInt(endLoc), FlatHelpers.RGBtoInt(red, green, blue));
                builder.addAction(action, Action.IndicatorLineAction);
            });
        }

        public void addDied(int id) {
            diedIds.add(id);
        }

        // public void addTrap(Trap trap) {
        //     trapAddedIds.add(trap.getId());
        //     MapLocation loc = trap.getLocation();
        //     trapAddedX.add(loc.x);
        //     trapAddedY.add(loc.y);
        //     trapAddedTypes.add(FlatHelpers.getSchemaTrapTypeFromTrapType(trap.getType()));
        //     trapAddedTeams.add(TeamMapping.id(trap.getTeam()));
        // }

        // public void addTriggeredTrap(int id) {
        //     trapTriggeredIds.add(id);
        // }

        private int locationToInt(MapLocation loc) {
            return loc.x + this.currentMapWidth * loc.y;
        }

        private void clearRoundData() {
            this.teamIDs.clear();
            this.teamCatDamage.clear();
            this.teamCheeseTransferred.clear();
            this.teamAliveRatKings.clear();
            this.teamAliveBabyRats.clear();
            this.teamDirtCount.clear();
            this.teamRatTrapCount.clear();
            this.teamCatTrapCount.clear();

            // this.trapAddedIds.clear();
            // this.trapAddedX.clear();
            // this.trapAddedY.clear();
            // this.trapAddedTypes.clear();
            // this.trapAddedTeams.clear();
            // this.trapTriggeredIds.clear();

            this.diedIds.clear();
        }

        private void clearMatchData() {
            clearRoundData();
            this.timelineMarkerTeams.clear();
            this.timelineMarkerColors.clear();
            this.timelineMarkerLabels.clear();
            this.timelineMarkerRounds.clear();
        }
    }

    public class FlatBufferBuilderWrapper extends FlatBufferBuilder {
        private ArrayList<Integer> turnOffsets = new ArrayList<>();
        private ArrayList<Integer> actionOffsets = new ArrayList<>();
        private ArrayList<Byte> actionTypes = new ArrayList<>();

        public FlatBufferBuilderWrapper() {
            super();
        }

        public FlatBufferBuilderWrapper(ByteBuffer data) {
            super(data);
        }

        public void addAction(int offset, byte actionType) {
            this.actionOffsets.add(offset);
            this.actionTypes.add(actionType);
        }

        public void startTurn() {
            int actionsOffset = Turn.createActionsVector(this,
                    ArrayUtils.toPrimitive(this.actionOffsets.toArray(new Integer[this.actionOffsets.size()])));
            int actionTypesOffsets = Turn.createActionsTypeVector(this,
                    ArrayUtils.toPrimitive(this.actionTypes.toArray(new Byte[this.actionTypes.size()])));

            Turn.startTurn(this);
            Turn.addActions(this, actionsOffset);
            Turn.addActionsType(this, actionTypesOffsets);
        }

        public void finishTurn() {
            int turnOffset = Turn.endTurn(this);

            this.turnOffsets.add(turnOffset);

            // Reset per-turn data
            this.actionOffsets.clear();
            this.actionTypes.clear();
        }

        public void startRound() {
            int turnsOffset = Round.createTurnsVector(this,
                    ArrayUtils.toPrimitive(this.turnOffsets.toArray(new Integer[this.turnOffsets.size()])));

            Round.startRound(this);
            Round.addTurns(this, turnsOffset);

            this.turnOffsets.clear();
        }

        public int finishRound() {
            int round = Round.endRound(this);
            return round;
        }
    }
}
