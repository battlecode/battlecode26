package battlecode.world;

import battlecode.common.*;
import battlecode.instrumenter.profiler.ProfilerCollection;
import battlecode.schema.Action;
import battlecode.schema.GameMap;
import battlecode.server.ErrorReporter;
import battlecode.server.GameMaker;
import battlecode.server.GameState;
import battlecode.util.FlatHelpers;
import battlecode.world.control.RobotControlProvider;

import java.util.*;

/**
 * The primary implementation of the GameWorld interface for containing and
 * modifying the game map and the objects on it.
 */
public class GameWorld {
    /**
     * The current round we're running.
     */
    protected int currentRound;

    /**
     * Whether we're running.
     */
    protected boolean running = true;
    protected boolean isCooperation = true;

    protected final IDGenerator idGenerator;
    protected final GameStats gameStats;

    private boolean[] walls;
    private boolean[] dirt;

    private int[] cheeseAmounts;
    private InternalRobot[][] robots;
    private Trap[] trapLocations;
    private ArrayList<Trap>[] trapTriggers;
    private HashMap<TrapType, Integer> trapCounts;
    private int trapId;
    private final LiveMap gameMap;
    private final TeamInfo teamInfo;
    private final ObjectInfo objectInfo;

    private int[] currentNumberUnits = { 0, 0 };

    private Map<Team, ProfilerCollection> profilerCollections;

    private final RobotControlProvider controlProvider;
    private Random rand;
    private final GameMaker.MatchMaker matchMaker;

    // Whether there is a ruin on each tile, indexed by location
    private boolean[] allCheeseMinesByLoc;
    // list of all cheese mines
    private ArrayList<CheeseMine> cheeseMines;
    private CheeseMine[] cheeseMineLocs;

    private int numCats;

    private int[] sharedArray;
    private int[] persistentArray;

    public int symmetricY(int y) {
        return symmetricY(y, gameMap.getSymmetry());
    }

    public int symmetricX(int x) {
        return symmetricX(x, gameMap.getSymmetry());
    }

    public int symmetricY(int y, MapSymmetry symmetry) {
        switch (symmetry) {
            case VERTICAL:
                return y;
            case HORIZONTAL:
            case ROTATIONAL:
            default:
                return gameMap.getHeight() - 1 - y;
        }
    }

    public int symmetricX(int x, MapSymmetry symmetry) {
        switch (symmetry) {
            case HORIZONTAL:
                return x;
            case VERTICAL:
            case ROTATIONAL:
            default:
                return gameMap.getWidth() - 1 - x;
        }
    }

    public MapLocation symmetryLocation(MapLocation p) {
        return new MapLocation(symmetricX(p.x), symmetricY(p.y));
    }

    @SuppressWarnings("unchecked")
    public GameWorld(LiveMap gm, RobotControlProvider cp, GameMaker.MatchMaker matchMaker) {
        int width = gm.getWidth();
        int height = gm.getHeight();
        int numSquares = width * height;
        this.walls = gm.getWallArray();
        this.dirt = gm.getDirtArray();
        this.cheeseAmounts = gm.getCheeseArray();
        this.robots = new InternalRobot[width][height]; // if represented in cartesian, should be height-width, but this
                                                        // should allow us to index x-y
        this.currentRound = 0;
        this.idGenerator = new IDGenerator(gm.getSeed());
        this.gameStats = new GameStats();
        this.gameMap = gm;
        this.objectInfo = new ObjectInfo(gm);
        this.trapCounts = new HashMap<>();
        trapCounts.put(TrapType.CAT_TRAP, 0);
        trapCounts.put(TrapType.RAT_TRAP, 0);
        trapTriggers = new ArrayList[numSquares];
        for (int i = 0; i < trapTriggers.length; i++){
            trapTriggers[i] = new ArrayList<>();
        }

        this.profilerCollections = new HashMap<>();

        this.controlProvider = cp;
        this.rand = new Random(this.gameMap.getSeed());
        this.matchMaker = matchMaker;

        this.controlProvider.matchStarted(this);

        this.teamInfo = new TeamInfo(this);
        this.teamInfo.addCheese(Team.A, GameConstants.INITIAL_TEAM_CHEESE);
        this.teamInfo.addCheese(Team.B, GameConstants.INITIAL_TEAM_CHEESE);

        // Write match header at beginning of match
        this.matchMaker.makeMatchHeader(this.gameMap);

        this.allCheeseMinesByLoc = gm.getCheeseMineArray();
        this.cheeseMines = new ArrayList<CheeseMine>();
        this.cheeseMineLocs = new CheeseMine[numSquares];

        this.numCats = 0;

        for (int i = 0; i < numSquares; i++) {
            if (this.allCheeseMinesByLoc[i]) {
                CheeseMine newMine = new CheeseMine(indexToLocation(i), GameConstants.SQ_CHEESE_SPAWN_RADIUS, null);
                this.cheeseMines.add(newMine);
                cheeseMineLocs[i] = newMine;
            }
        }

        for (CheeseMine mine : this.cheeseMines) {
            MapLocation symLoc = symmetryLocation(mine.getLocation());
            mine.setPair(cheeseMineLocs[locationToIndex(symLoc)]);
        }

        this.sharedArray = new int[GameConstants.SHARED_ARRAY_SIZE];
        this.persistentArray = new int[GameConstants.PERSISTENT_ARRAY_SIZE];
        // TODO make persistent array last between matches

        RobotInfo[] initialBodies = gm.getInitialBodies();

        for (int i = 0; i < initialBodies.length; i++) {
            RobotInfo robotInfo = initialBodies[i];
            MapLocation newLocation = robotInfo.location.translate(gm.getOrigin().x, gm.getOrigin().y);
            spawnRobot(robotInfo.ID, robotInfo.type, newLocation, robotInfo.team);
            System.out.println("Has cheese amount" + robotInfo.cheeseAmount);
        }
    }

    /**
     * Run a single round of the game.
     *
     * @return the state of the game after the round has run
     */
    public synchronized GameState runRound() {
        if (!this.isRunning()) {
            List<ProfilerCollection> profilers = new ArrayList<>(2);
            if (!profilerCollections.isEmpty()) {
                profilers.add(profilerCollections.get(Team.A));
                profilers.add(profilerCollections.get(Team.B));
            }

            // Write match footer if game is done
            matchMaker.makeMatchFooter(gameStats.getWinner(), gameStats.getDominationFactor(), currentRound, profilers);
            return GameState.DONE;
        }

        try {
            this.processBeginningOfRound();
            this.controlProvider.roundStarted();

            updateDynamicBodies();

            this.controlProvider.roundEnded();
            this.processEndOfRound();

            if (!this.isRunning()) {
                this.controlProvider.matchEnded();
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
            // TODO throw out file?
            return GameState.DONE;
        }
        // todo: should I end the round here or in processEndofRound?
        return GameState.RUNNING;
    }

    private void updateDynamicBodies() {
        objectInfo.eachDynamicBodyByExecOrder((body) -> {
            if (body instanceof InternalRobot) {
                return updateRobot((InternalRobot) body);
            } else {
                throw new RuntimeException("non-robot body registered as dynamic");
            }
        });
    }

    private boolean updateRobot(InternalRobot robot) {
        robot.processBeginningOfTurn();
        this.controlProvider.runRobot(robot);
        robot.setBytecodesUsed(this.controlProvider.getBytecodesUsed(robot));
        robot.processEndOfTurn();

        // If the robot terminates but the death signal has not yet
        // been visited:

        if (this.controlProvider.getTerminated(robot) && objectInfo.getRobotByID(robot.getID()) != null
                && robot.getLocation() != null) {
            destroyRobot(robot.getID());
        }

        return true;
    }

    // *********************************
    // ****** BASIC MAP METHODS ********
    // *********************************

    public int getMapSeed() {
        return this.gameMap.getSeed();
    }

    public LiveMap getGameMap() {
        return this.gameMap;
    }

    public TeamInfo getTeamInfo() {
        return this.teamInfo;
    }

    public GameStats getGameStats() {
        return this.gameStats;
    }

    public ObjectInfo getObjectInfo() {
        return this.objectInfo;
    }

    public GameMaker.MatchMaker getMatchMaker() {
        return this.matchMaker;
    }

    public Team getWinner() {
        return this.gameStats.getWinner();
    }

    public boolean isRunning() {
        return this.running;
    }

    public int getCurrentRound() {
        return this.currentRound;
    }

    public boolean isCooperation() {
        return this.isCooperation;
    }

    public boolean getWall(MapLocation loc) {
        return this.walls[locationToIndex(loc)];
    }

    public boolean getDirt(MapLocation loc) {
        return this.dirt[locationToIndex(loc)];
    }

    /**
     * Allows a robot to add or remove dirt (add = true, remove = false)
     * to a location on the map
     * 
     * @param loc, the location in MapLocation to add/remove dirt
     * @param val, true if adding dirt, false if removing dirt
     * 
     * @returns void, modifies GameWorld's dirt array in place
     */
    public void setDirt(MapLocation loc, boolean val) {
        if (loc == null)
            return;
        int mapIndex = locationToIndex(loc);
        this.dirt[mapIndex] = val;

    }

    public int getCheeseAmount(MapLocation loc) {
        return this.cheeseAmounts[locationToIndex(loc)];
    }

    public void removeCheese(MapLocation loc) {
        this.cheeseAmounts[locationToIndex(loc)] = 0;
    }

    public void addCheese(MapLocation loc, int amount) {
        this.cheeseAmounts[locationToIndex(loc)] += amount;
    }

    public int getNumCats(){
        return this.numCats;
    }

    public boolean isPassable(MapLocation loc) {
        return !(this.walls[locationToIndex(loc)]
                || this.dirt[locationToIndex(loc)]);
    }

    /**
     * Helper method that converts a location into an index.
     * 
     * @param loc the MapLocation
     */
    public int locationToIndex(MapLocation loc) {
        return this.gameMap.locationToIndex(loc);
    }

    /**
     * Helper method that converts an index into a location.
     * 
     * @param idx the index
     */
    public MapLocation indexToLocation(int idx) {
        return gameMap.indexToLocation(idx);
    }

    // ***********************************
    // ****** CHEESE METHODS *************
    // ***********************************

    public void spawnCheese(CheeseMine mine) {
        boolean spawn = rand.nextFloat() < mine.generationProbability(currentRound);
        if (spawn) {
            int dx = rand.nextInt(-GameConstants.SQ_CHEESE_SPAWN_RADIUS, GameConstants.SQ_CHEESE_SPAWN_RADIUS);
            int dy = rand.nextInt(-GameConstants.SQ_CHEESE_SPAWN_RADIUS, GameConstants.SQ_CHEESE_SPAWN_RADIUS);

            // if rotational, flip both symmetries, if vertical/horizontal, only flip the
            // corresponding one
            int pair_dx = gameMap.getSymmetry() == MapSymmetry.VERTICAL ? dx : -dx;
            int pair_dy = gameMap.getSymmetry() == MapSymmetry.HORIZONTAL ? dy : -dy;
            CheeseMine pairedMine = mine.getPair();

            int cheeseX = mine.getLocation().x + dx;
            int cheeseY = mine.getLocation().y + dy;

            int pairedX = pairedMine.getLocation().x + pair_dx;
            int pairedY = pairedMine.getLocation().y + pair_dy;

            addCheese(new MapLocation(cheeseX, cheeseY), GameConstants.CHEESE_SPAWN_AMOUNT);
            addCheese(new MapLocation(pairedX, pairedY), GameConstants.CHEESE_SPAWN_AMOUNT);

            mine.setLastRound(this.currentRound);
            pairedMine.setLastRound(this.currentRound);

            // matchMaker.addCheeseSpawnAction(mine, loc); TODO: ADD MATCHMAKER
        }
    }

    public boolean hasCheeseMine(MapLocation loc){
        return this.cheeseMineLocs[locationToIndex(loc)] != null;
    }

    // ***********************************
    // ****** TRAP METHODS **************
    // ***********************************

    public Trap getTrap(MapLocation loc) {
        return this.trapLocations[locationToIndex(loc)];
    }

    public boolean hasTrap(MapLocation loc) {
        return (this.trapLocations[locationToIndex(loc)] != null);
    }

    public boolean hasRatTrap(MapLocation loc) {
        Trap trap = this.trapLocations[locationToIndex(loc)];
        return (trap != null && trap.getType() == TrapType.RAT_TRAP);
    }

    public boolean hasCatTrap(MapLocation loc) {
        Trap trap = this.trapLocations[locationToIndex(loc)];
        return (trap != null && trap.getType() == TrapType.CAT_TRAP);
    }

    public ArrayList<Trap> getTrapTriggers(MapLocation loc) {
        return this.trapTriggers[locationToIndex(loc)];
    }

    public void placeTrap(MapLocation loc, TrapType type, Team team) {
        Trap trap = new Trap(loc, type, team, trapId);
        
        int idx = locationToIndex(loc);
        this.trapLocations[idx] = trap;
        this.cheeseAmounts[idx] = Math.max(this.cheeseAmounts[idx], type.spawnCheeseAmount);

        for (MapLocation adjLoc : getAllLocationsWithinRadiusSquared(loc, type.triggerRadiusSquared)) {
            this.trapTriggers[locationToIndex(adjLoc)].add(trap);
        }

        matchMaker.addTrap(trap);
        this.trapCounts.put(type, this.trapCounts.get(type) + 1);
        trapId++;
    }

    public void removeTrap(MapLocation loc) {
        Trap trap = this.trapLocations[locationToIndex(loc)];
        if (trap == null) {
            return;
        }
        TrapType type = trap.getType();
        this.trapCounts.put(type, this.trapCounts.get(type) - 1);
        this.trapLocations[locationToIndex(loc)] = null;

        for (MapLocation adjLoc : getAllLocationsWithinRadiusSquared(loc, type.triggerRadiusSquared)) {
            this.trapTriggers[locationToIndex(adjLoc)].remove(trap);
        }
    } 

    public int getTrapCount(TrapType type) {
        return this.trapCounts.get(type);
    }

    public void triggerTrap(Trap trap, InternalRobot robot) {
        MapLocation loc = trap.getLocation();
        TrapType type = trap.getType();
        
        robot.setMovementCooldownTurns(type.stunTime);
        robot.addHealth(-type.damage);
        if (robot.getType().isCatType()){
            this.teamInfo.addDamageToCats(trap.getTeam(), type.damage);
        }
        //TODO once the cat exists, alert cat of trap trigger
        //TODO once backstab status exists, update that

        for (MapLocation adjLoc : getAllLocationsWithinRadiusSquared(loc, type.triggerRadiusSquared)) {
            this.trapTriggers[locationToIndex(adjLoc)].remove(trap);
        }

        this.trapLocations[locationToIndex(loc)] = null;
        matchMaker.addTriggeredTrap(trap.getId());
        // matchMaker.addAction(robot.getID(), FlatHelpers.getTrapActionFromTrapType(type),
        //         locationToIndex(trap.getLocation()));
    }

    // ***********************************
    // ****** ROBOT METHODS **************
    // ***********************************

    public InternalRobot getRobot(MapLocation loc) {
        return this.robots[loc.x - this.gameMap.getOrigin().x][loc.y - this.gameMap.getOrigin().y];
    }

    public void moveRobot(MapLocation start, MapLocation end) {
        addRobot(end, getRobot(start));
        removeRobot(start);
    }

    public void addRobot(MapLocation loc, InternalRobot robot) {
        this.robots[loc.x - this.gameMap.getOrigin().x][loc.y - this.gameMap.getOrigin().y] = robot;
    }

    public void removeRobot(MapLocation loc) {
        this.robots[loc.x - this.gameMap.getOrigin().x][loc.y - this.gameMap.getOrigin().y] = null;
    }

    public InternalRobot[] getAllRobotsWithinRadiusSquared(MapLocation center, int radiusSquared) {
        return getAllRobotsWithinRadiusSquared(center, radiusSquared, null);
    }

    public InternalRobot[] getAllRobotsWithinRadiusSquared(MapLocation center, int radiusSquared, Team team) {
        ArrayList<InternalRobot> returnRobots = new ArrayList<InternalRobot>();
        for (MapLocation newLocation : getAllLocationsWithinRadiusSquared(center, radiusSquared))
            if (getRobot(newLocation) != null) {
                if (team == null || getRobot(newLocation).getTeam() == team)
                    returnRobots.add(getRobot(newLocation));
            }
        return returnRobots.toArray(new InternalRobot[returnRobots.size()]);
    }

    public InternalRobot[] getAllRobotsWithinConeRadiusSquared(MapLocation center, Direction lookDirection, double totalAngle, int radiusSquared) {
        return getAllRobotsWithinConeRadiusSquared(center, lookDirection, totalAngle, radiusSquared, null);
    }

    public InternalRobot[] getAllRobotsWithinConeRadiusSquared(MapLocation center, Direction lookDirection, double totalAngle, int radiusSquared, Team team) {
        ArrayList<InternalRobot> returnRobots = new ArrayList<InternalRobot>();
        for (MapLocation newLocation : getAllLocationsWithinConeRadiusSquared(center, lookDirection, totalAngle, radiusSquared))
            if (getRobot(newLocation) != null) {
                if (team == null || getRobot(newLocation).getTeam() == team)
                    returnRobots.add(getRobot(newLocation));
            }
        return returnRobots.toArray(new InternalRobot[returnRobots.size()]);
    }

    public InternalRobot[] getAllRobots(Team team) {
        ArrayList<InternalRobot> returnRobots = new ArrayList<InternalRobot>();
        for (MapLocation newLocation : getAllLocations()) {
            if (getRobot(newLocation) != null && (team == null || getRobot(newLocation).getTeam() == team)) {
                returnRobots.add(getRobot(newLocation));
            }
        }
        return returnRobots.toArray(new InternalRobot[returnRobots.size()]);
    }

    public MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared) {
        return getAllLocationsWithinConeRadiusSquaredWithoutMap(
                this.gameMap.getOrigin(),
                this.gameMap.getWidth(),
                this.gameMap.getHeight(),
                center, Direction.CENTER, 360, radiusSquared);
    }
    
    public MapLocation[] getAllLocationsWithinConeRadiusSquared(MapLocation center, Direction lookDirection, double totalAngle, int radiusSquared) {
        return getAllLocationsWithinConeRadiusSquaredWithoutMap(
            this.gameMap.getOrigin(),
            this.gameMap.getWidth(),
            this.gameMap.getHeight(),
            center,
            lookDirection,
            totalAngle, radiusSquared
        );
    }

    public static MapLocation[] getAllLocationsWithinConeRadiusSquaredWithoutMap(MapLocation origin,
                                                                            int width, int height,
                                                                            MapLocation center,
                                                                            Direction lookDirection,
                                                                            double angle, int radiusSquared) {
        ArrayList<MapLocation> returnLocations = new ArrayList<MapLocation>();
        int ceiledRadius = (int) Math.ceil(Math.sqrt(radiusSquared)) + 1; // add +1 just to be safe
        int minX = Math.max(center.x - ceiledRadius, origin.x);
        int minY = Math.max(center.y - ceiledRadius, origin.y);
        int maxX = Math.min(center.x + ceiledRadius, origin.x + width - 1);
        int maxY = Math.min(center.y + ceiledRadius, origin.y + height - 1);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                MapLocation newLocation = new MapLocation(x, y);

                if (center.isWithinDistanceSquared(newLocation, radiusSquared, lookDirection, angle)) {
                    returnLocations.add(newLocation);
                }
            }
        }
        return returnLocations.toArray(new MapLocation[returnLocations.size()]);
    }

    /**
     * @return all of the locations on the grid
     */
    private MapLocation[] getAllLocations() {
        return getAllLocationsWithinRadiusSquared(new MapLocation(0, 0), Integer.MAX_VALUE);
    }

    // *********************************
    // ****** GAMEPLAY *****************
    // *********************************

    public void processBeginningOfRound() {
        currentRound++;

        this.getMatchMaker().startRound(currentRound);
        // Process beginning of each robot's round
        objectInfo.eachRobot((robot) -> {
            robot.processBeginningOfRound();
            return true;
        });
    }

    public void setWinner(Team t, DominationFactor d) {
        gameStats.setWinner(t);
        gameStats.setDominationFactor(d);
    }

    /**
     * @return whether a team's rat kings are all dead
     */
    public boolean setWinnerIfKilledAllRatKings() {
        // all rat kings dead
        
        if (this.getTeamInfo().getNumRatKings(Team.A) == 0){
            gameStats.setWinner(Team.B);
            gameStats.setDominationFactor(DominationFactor.KILL_ALL_RAT_KINGS);
            return true;
        }
        else if (this.getTeamInfo().getNumRatKings(Team.B) == 0){
            gameStats.setWinner(Team.A);
            gameStats.setDominationFactor(DominationFactor.KILL_ALL_RAT_KINGS);
            return true;
        }

        return false;
    }

    /**
     * @return whether all cats dead
     */
    public boolean setWinnerifAllCatsDead() {        
        if(this.getNumCats() == 0){
            // find out which team won via points
            if (setWinnerIfMorePoints())
                return true;
            if (setWinnerIfMoreCheese())
                return true;
            if (setWinnerIfMoreRatsAlive())
                return true;
            setWinnerArbitrary();

            return true;
        }
        return false;

    }

    /**
     * @return whether a team has more cheese
     */
    public boolean setWinnerIfMoreCheese() {
        int[] totalCheeseValues = new int[2];

        // consider team reserves
        totalCheeseValues[Team.A.ordinal()] += this.teamInfo.getCheese(Team.A);
        totalCheeseValues[Team.B.ordinal()] += this.teamInfo.getCheese(Team.B);

        if (totalCheeseValues[Team.A.ordinal()] > totalCheeseValues[Team.B.ordinal()]) {
            // TODO: add new tiebreakers to domination factor
            setWinner(Team.A, DominationFactor.MORE_CHEESE);
            return true;
        } else if (totalCheeseValues[Team.B.ordinal()] > totalCheeseValues[Team.A.ordinal()]) {
            setWinner(Team.B, DominationFactor.MORE_CHEESE);
            return true;
        }
        return false;
    }

    /**
     * @return whether a team has more allied rats alive
     */
    public boolean setWinnerIfMoreRatsAlive() {
        int[] totalRobotsAlive = new int[2];

        for (UnitType type : UnitType.values()) {
            if (type.isRatType()) {
                totalRobotsAlive[Team.A.ordinal()] += this.getObjectInfo().getRobotTypeCount(Team.A, type);
                totalRobotsAlive[Team.B.ordinal()] += this.getObjectInfo().getRobotTypeCount(Team.B, type);
            }
        }

        if (totalRobotsAlive[Team.A.ordinal()] > totalRobotsAlive[Team.B.ordinal()]) {
            setWinner(Team.A, DominationFactor.MORE_ROBOTS_ALIVE);
            return true;
        } else if (totalRobotsAlive[Team.B.ordinal()] > totalRobotsAlive[Team.A.ordinal()]) {
            setWinner(Team.B, DominationFactor.MORE_ROBOTS_ALIVE);
            return true;
        }
        return false;
    }

    /**
     * @return whether a team has more points depending on the game state
     */
    public boolean setWinnerIfMorePoints() {
        double cat_weight;
        double king_weight;
        double cheese_weight;

        if(isCooperation()){
            cat_weight = 0.5;
            king_weight = 0.3;
            cheese_weight = 0.2;
        }
        else{
            cat_weight = 0.3;
            king_weight = 0.5;
            cheese_weight = 0.2;
        }

        ArrayList<Integer> teamPoints = new ArrayList<>();

        for (Team team : List.of(Team.A, Team.B)) {
            int points = (int) (
                cat_weight * (100) +
                king_weight * teamInfo.getNumRatKings(team) +
                cheese_weight * teamInfo.getCheese(team)); // TODO: Update this to the correct points formula
            this.teamInfo.addPoints(team, points);
            teamPoints.add(points);
        }
        if (teamPoints.getFirst() > teamPoints.getLast()) {
            setWinner(Team.A, DominationFactor.MORE_POINTS);
            return true;
        } else if (teamPoints.getFirst() < teamPoints.getLast()) {
            setWinner(Team.B, DominationFactor.MORE_POINTS);
            return true;
        }

        return false;
    }

    /**
     * Sets a winner arbitrarily. Hopefully this is actually random.
     */
    public void setWinnerArbitrary() {
        setWinner(Math.random() < 0.5 ? Team.A : Team.B, DominationFactor.WON_BY_DUBIOUS_REASONS);
    }

    public boolean timeLimitReached() {
        return currentRound >= this.gameMap.getRounds();
    }
    
    /**
     * Checks end of match and then decides winner based on tiebreak conditions
     */
    public void checkEndOfMatch() {
        if (timeLimitReached() && gameStats.getWinner() == null) {
            if (setWinnerIfMorePoints())
                return;
            if (setWinnerIfMoreCheese())
                return;
            if (setWinnerIfMoreRatsAlive())
                return;
            setWinnerArbitrary();
        }
    }

    private void checkWin(Team team) {
        if(gameStats.getWinner() != null){ // to avoid overriding previously set win
            return;
        }
        // killed all rat kings?
       if(setWinnerIfKilledAllRatKings())
            return;
        // all cats dead
        if(setWinnerifAllCatsDead()){
            return;
        }
        
        // TODO: if cooperation, even if cat dies, game continues?    
        throw new InternalError("Reporting incorrect win");
    }

    public void processEndOfRound() {
        for (CheeseMine mine : this.cheeseMines) {
            spawnCheese(mine);
        }

        // TODO: new team info stuff in matchmaker
        this.matchMaker.addTeamInfo(Team.A, this.teamInfo.getCheese(Team.A));
        this.matchMaker.addTeamInfo(Team.B, this.teamInfo.getCheese(Team.B));
        this.teamInfo.processEndOfRound();

        this.getMatchMaker().endRound();

        checkEndOfMatch();

        if (gameStats.getWinner() != null)
            running = false;
    }
    
    // *********************************
    // ****** SPAWNING *****************
    // *********************************

    public int spawnRobot(int ID, UnitType type, MapLocation location, Team team) {
        // TODO: what direction should robots start facing?
        // IMO, towards center of the map to be fair
        
        InternalRobot robot = new InternalRobot(this, ID, team, type, location, Direction.NORTH);

        for (MapLocation loc : type.getAllLocations(location)) {
            addRobot(loc, robot);
        }

        objectInfo.createRobot(robot);
        controlProvider.robotSpawned(robot);

        if (type.isRatType()){
            this.teamInfo.addRats(1, team);
        }
        else if(type.isRatKingType()){
            this.teamInfo.addRatKings(1, team);
        }
        else if(type.isCatType()){
            this.numCats += 1;
        }
            

        if (!type.isCatType()){
            this.currentNumberUnits[team.ordinal()] += 1;
        }
        return ID;
    }

    public int spawnRobot(UnitType type, MapLocation location, Team team) {
        int ID = idGenerator.nextID();

        return spawnRobot(ID, type, location, team);
    }

    public void squeak(InternalRobot robot, Message message) {
        MapLocation robotLoc = robot.getLocation();
        MapLocation[] locations = getAllLocationsWithinRadiusSquared(robotLoc, GameConstants.SQUEAK_RADIUS_SQUARED);

        for (MapLocation loc : locations){
            InternalRobot otherRobot = getRobot(loc);

            if (otherRobot != null && otherRobot.getTeam() == robot.getTeam()) {
                otherRobot.addMessage(message.copy());
            }
            
            //TODO alert cats
        }

        matchMaker.addSqueakAction(robotLoc);
    }

    public void writeSharedArray(int index, int value) {
        this.sharedArray[index] = value;
    }

    public int readSharedArray(int index) {
        return this.sharedArray[index];
    }

    public void writePersistentArray(int index, int value) {
        this.persistentArray[index] = value;
    }

    public int readPersistentArray(int index) {
        return this.persistentArray[index];
    }

    // *********************************
    // ****** DESTROYING ***************
    // *********************************

    /**
     * Permanently destroy a robot
     */
    public void destroyRobot(int id) {
        destroyRobot(id, false, false);
    }

    public void updateCatHealth(int id, int newHealth){
        objectInfo.updateCatHealth(id, newHealth);
    }

    public void destroyRobot(int id, boolean fromException, boolean fromDamage) {
        InternalRobot robot = objectInfo.getRobotByID(id);
        Team robotTeam = robot.getTeam();
        MapLocation loc = robot.getLocation();

        // check win
        if(robot.getType() == UnitType.RAT_KING && this.getTeamInfo().getNumRatKings(robot.getTeam()) == 0){
            System.out.println("DEBUGGING: number of rat kings = " + this.getTeamInfo().getNumRatKings(robot.getTeam()));
            checkWin(robotTeam);
        }
        else if(robot.getType() == UnitType.CAT && this.getNumCats() == 0){
            System.out.println("DEBUGGING: number of cats = " + this.getNumCats());
            checkWin(robotTeam);
        }

        if (loc != null) {
            if (robot.getType().isRatType()){
                this.teamInfo.addRats(-1, robotTeam);
            }
            else if (robot.getType().isRatKingType()){
                this.teamInfo.addRatKings(-1, robotTeam);
            }
            else if (robot.getType().isCatType()){
                this.numCats -= 1;
            }
            for (MapLocation robotLoc : robot.getAllPartLocations()) {
                removeRobot(robotLoc);
            }
        }
        controlProvider.robotKilled(robot);
        objectInfo.destroyRobot(id);
        if (fromDamage || fromException)
            matchMaker.addDieAction(id, fromException);
        else
            matchMaker.addDied(id);
        this.currentNumberUnits[robot.getTeam().ordinal()] -= 1;
    }

    // *********************************
    // ********* PROFILER **************
    // *********************************

    public void setProfilerCollection(Team team, ProfilerCollection profilerCollection) {
        if (profilerCollections == null) {
            profilerCollections = new HashMap<>();
        }
        profilerCollections.put(team, profilerCollection);
    }
}
