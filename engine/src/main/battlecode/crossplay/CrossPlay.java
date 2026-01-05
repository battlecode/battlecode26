package battlecode.crossplay;

import java.util.ArrayList;
import org.json.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;

import battlecode.common.*;
import battlecode.instrumenter.stream.RoboPrintStream;

import static battlecode.crossplay.CrossPlayMethod.*;
import static battlecode.crossplay.CrossPlayObjectType.*;

/**
 * Allows bots written in different languages to be run by the Java engine using a message-passing system.
 * Any language can be supported as long as a file analogous to runner.py is written.
 * Battlecode 2026 supports Java and Python.
 */
public class CrossPlay {
    public static final String
        CROSS_PLAY_DIR = "crossplay_temp", // temporary directory for cross-play files
        MESSAGE_FILE_JAVA = "messages_java.json", // messages from the java engine
        MESSAGE_FILE_OTHER = "messages_other.json", // messages from the other language's runner script
        LOCK_FILE_JAVA = "lock_java.txt", // lock file created by the java engine
        LOCK_FILE_OTHER = "lock_other.txt", // lock file created by the other language's runner script
        STARTED_FILE_JAVA = "started_java.txt", // file created by the java engine when it starts
        STARTED_FILE_OTHER = "started_other.txt"; // file created by the other language's runner

    private final boolean finalizer;
    private boolean initialized;
    private ArrayList<Object> objects;
    private RobotController rc;
    private CrossPlayReference rcRef;
    private int roundNum;
    private Team team;
    private int id;
    private OutputStream out;

    public CrossPlay() {
        this.objects = new ArrayList<>();
        this.finalizer = false;
        this.initialized = false;
    }

    public CrossPlay(boolean finalizer) {
        this.objects = new ArrayList<>();
        this.finalizer = finalizer;
        this.initialized = false;
    }

    private void clearObjects() {
        this.objects.clear();
    }

    public static void resetFiles() {
        try {
            Path crossPlayDir = Paths.get(CROSS_PLAY_DIR);

            if (!Files.exists(crossPlayDir) || !Files.isDirectory(crossPlayDir)) {
                Files.createDirectory(crossPlayDir);
            } else if (Files.exists(crossPlayDir.resolve(STARTED_FILE_JAVA))) {
                System.out.println("DEBUGGING: Detected existing crossplay_temp/started_java.txt file. "
                    + "This indicates that a previous cross-play match did not terminate cleanly. "
                    + "Deleting the old crossplay_temp files.");
            } else if (Files.exists(crossPlayDir.resolve(STARTED_FILE_OTHER))) {
                System.out.println("DEBUGGING: Python cross-play runner already started. Using existing cross-play temp directory.");
                return;
            }

            Files.deleteIfExists(crossPlayDir.resolve(MESSAGE_FILE_JAVA));
            Files.deleteIfExists(crossPlayDir.resolve(MESSAGE_FILE_OTHER));
            Files.deleteIfExists(crossPlayDir.resolve(LOCK_FILE_JAVA));
            Files.deleteIfExists(crossPlayDir.resolve(LOCK_FILE_OTHER));

            Files.createFile(crossPlayDir.resolve(STARTED_FILE_JAVA));
        } catch (Exception e) {
            throw new CrossPlayException("Failed to clear cross-play lock files.");
        }
    }

    public static void clearTempFiles() {
        try {
            Path crossPlayDir = Paths.get(CROSS_PLAY_DIR);

            if (Files.exists(crossPlayDir)) {
                Files.deleteIfExists(crossPlayDir.resolve(MESSAGE_FILE_JAVA));
                Files.deleteIfExists(crossPlayDir.resolve(MESSAGE_FILE_OTHER));
                Files.deleteIfExists(crossPlayDir.resolve(LOCK_FILE_JAVA));
                Files.deleteIfExists(crossPlayDir.resolve(LOCK_FILE_OTHER));
                Files.deleteIfExists(crossPlayDir.resolve(STARTED_FILE_JAVA));
                Files.deleteIfExists(crossPlayDir.resolve(STARTED_FILE_OTHER));
                Files.delete(crossPlayDir);
            }
        } catch (IOException e) {
            throw new CrossPlayException("Failed to clear cross-play lock files.");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getLiteralValue(CrossPlayObject obj) {
        if (obj instanceof CrossPlayLiteral lit) {
            Object value = lit.value;

            try {
                return (T) value;
            } catch (ClassCastException e) {
                throw new CrossPlayException("Tried to get object of type " + obj.type + " but it does not match expected type.");
            }
        } else {
            throw new CrossPlayException("Tried to get value of non-literal cross-play object");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getObject(CrossPlayObject obj) {
        if (obj instanceof CrossPlayReference ref) {
            Object rawObj = this.objects.get(ref.objectId);

            try {
                return (T) rawObj;
            } catch (ClassCastException e) {
                throw new CrossPlayException("Tried to get object of type " + obj.type + " but it does not match expected type.");
            }
        } else {
            throw new CrossPlayException("Tried to retrieve Java value of non-reference cross-play object");
        }
    }

    private void setObject(CrossPlayReference ref, Object value) {
        if (ref.objectId >= this.objects.size()) {
            // extend the array
            for (int i = this.objects.size(); i <= ref.objectId; i++) {
                this.objects.add(null);
            }
        }

        this.objects.set(ref.objectId, value);
    }

    private CrossPlayReference setNextObject(CrossPlayObjectType type, Object value) {
        CrossPlayReference ref = new CrossPlayReference(type, this.objects.size());
        setObject(ref, value);
        return ref;
    }

    public int runMessagePassing() {
        Path crossPlayDir = Paths.get(CROSS_PLAY_DIR);
        Path javaMessagePath = crossPlayDir.resolve(MESSAGE_FILE_JAVA);
        Path otherMessagePath = crossPlayDir.resolve(MESSAGE_FILE_OTHER);
        Path javaLockPath = crossPlayDir.resolve(LOCK_FILE_JAVA);
        Path otherLockPath = crossPlayDir.resolve(LOCK_FILE_OTHER);
        // System.out.println("Waiting for message Python -> Java...");

        while (true) {
            try {
                if (!Files.exists(otherMessagePath) || Files.exists(javaMessagePath) || Files.exists(otherLockPath)) {
                    Thread.sleep(0, 100000); // TODO currently 0.1 ms, maybe make shorter
                    // System.out.println("Still waiting for message Python -> Java...");
                    continue;
                }

                if (Files.exists(javaLockPath)) {
                    throw new CrossPlayException("Detected existing java lock file while waiting for other language's message."
                        + " This should never happen under normal operation.");
                }

                Files.createFile(javaLockPath);
                String messageContent = Files.readString(otherMessagePath);
                JSONObject messageJson = new JSONObject(messageContent);
                CrossPlayMessage message = CrossPlayMessage.fromJson(messageJson);

                // System.out.println("Received message Python -> Java: " + messageJson.toString());

                CrossPlayObject result = processMessage(message);
                String resultContent = result.toJson().toString();
                Files.writeString(javaMessagePath, resultContent);

                Files.delete(otherMessagePath);
                Files.delete(javaLockPath);

                if (message.method == END_TURN) {
                    // System.out.println("Received terminate message, ending cross-play message passing.");
                    int bytecodeUsed = getLiteralValue(message.params[0]);
                    return bytecodeUsed;
                } else if (this.finalizer && message.method == START_TURN) {
                    // System.out.println("Finalizer received start turn message, ending cross-play message passing.");
                    return 0;
                }

                // System.out.println("Sent response Java -> Python: " + resultContent);
                // System.out.println("Waiting for message Python -> Java...");
            } catch (InterruptedException e) {
                throw new CrossPlayException("Cross-play message passing thread was interrupted.");
            } catch (IOException e) {
                throw new CrossPlayException("Cross-play message passing failed due to file I/O error: " + e.getMessage());
            }
        }
    }

    private CrossPlayObject processMessage(CrossPlayMessage message) {
        CrossPlayObject result;
        RobotController rc;

        try {
            switch (message.method) {
                case INVALID:
                    throw new CrossPlayException("Received invalid cross-play method!");
                case START_TURN:
                    // System.out.println("Processing START_TURN");
                    if (this.finalizer) {
                        result = new CrossPlayLiteral(ARRAY, new CrossPlayObject[] {
                            CrossPlayLiteral.NULL,
                            CrossPlayLiteral.NULL,
                            CrossPlayLiteral.NULL,
                            CrossPlayLiteral.NULL,
                            CrossPlayLiteral.TRUE
                        });
                    } else {
                        result = new CrossPlayLiteral(ARRAY, new CrossPlayObject[] {
                            this.rcRef,
                            CrossPlayLiteral.ofInt(this.roundNum),
                            CrossPlayLiteral.ofTeam(this.team),
                            CrossPlayLiteral.ofInt(this.id),
                            CrossPlayLiteral.FALSE
                        });
                    }
                    break;
                case END_TURN:
                    // System.out.println("Processing END_TURN");
                    result = new CrossPlayLiteral(NULL, null);
                    break;
                case RC_GET_ROUND_NUM:
                    // System.out.println("Processing RC_GET_ROUND_NUM");
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = new CrossPlayLiteral(INTEGER, rc.getRoundNum());
                    break;
                case RC_GET_MAP_WIDTH:
                    // System.out.println("Processing RC_GET_MAP_WIDTH");
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = new CrossPlayLiteral(INTEGER, rc.getMapWidth());
                    break;
                case RC_GET_MAP_HEIGHT:
                    // System.out.println("Processing RC_GET_MAP_HEIGHT");
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = new CrossPlayLiteral(INTEGER, rc.getMapHeight());
                    break;
                case RC_IS_COOPERATION:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.isCooperation());
                    break;
                case RC_GET_ID:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getID());
                    break;
                case RC_GET_TEAM:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofTeam(rc.getTeam());
                    break;
                case RC_GET_LOCATION: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc0 = rc.getLocation();
                    result = CrossPlayLiteral.ofMapLocation(loc0);
                    break;
                }
                case RC_GET_ALL_PART_LOCATIONS: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation[] locs = rc.getAllPartLocations();
                    CrossPlayObject[] arr = new CrossPlayObject[locs.length];

                    for (int i = 0; i < locs.length; i++) {
                        arr[i] = CrossPlayLiteral.ofMapLocation(locs[i]);
                    }

                    result = CrossPlayLiteral.ofArray(arr);
                    break;
                }
                case RC_GET_DIRECTION:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = setNextObject(DIRECTION, rc.getDirection());
                    break;
                case RC_GET_HEALTH:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getHealth());
                    break;
                case RC_GET_RAW_CHEESE:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getRawCheese());
                    break;
                case RC_GET_GLOBAL_CHEESE:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getGlobalCheese());
                    break;
                case RC_GET_ALL_CHEESE:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getAllCheese());
                    break;
                case RC_GET_DIRT:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getDirt());
                    break;
                case RC_GET_TYPE:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getType().ordinal());
                    break;
                case RC_GET_CARRYING: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    RobotInfo carrying = rc.getCarrying();

                    if (carrying == null) {
                        result = CrossPlayLiteral.NULL;
                    } else {
                        result = setNextObject(ROBOT_INFO, carrying);
                    }

                    break;
                }
                case RC_IS_BEING_THROWN:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.isBeingThrown());
                    break;
                case RC_IS_BEING_CARRIED:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.isBeingCarried());
                    break;
                case RC_ON_THE_MAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.onTheMap(loc));
                    break;
                }
                case RC_CAN_SENSE_LOCATION: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canSenseLocation(loc));
                    break;
                }
                case RC_IS_LOCATION_OCCUPIED: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.isLocationOccupied(loc));
                    break;
                }
                case RC_CAN_SENSE_ROBOT_AT_LOCATION: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canSenseRobotAtLocation(loc));
                    break;
                }
                case RC_SENSE_ROBOT_AT_LOCATION: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    RobotInfo info = rc.senseRobotAtLocation(loc);

                    if (info == null) {
                        result = CrossPlayLiteral.NULL;
                    } else {
                        result = setNextObject(ROBOT_INFO, info);
                    }

                    break;
                }
                case RC_CAN_SENSE_ROBOT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int qid = getLiteralValue(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canSenseRobot(qid));
                    break;
                }
                case RC_SENSE_ROBOT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int qid = getLiteralValue(message.params[1]);
                    RobotInfo info = rc.senseRobot(qid);

                    if (info == null) {
                        result = CrossPlayLiteral.NULL;
                    } else {
                        result = setNextObject(ROBOT_INFO, info);
                    }

                    break;
                }
                case RC_SENSE_NEARBY_ROBOTS: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    RobotInfo[] infos = rc.senseNearbyRobots();
                    CrossPlayObject[] arr = new CrossPlayObject[infos.length];

                    for (int i = 0; i < infos.length; i++) {
                        arr[i] = setNextObject(ROBOT_INFO, infos[i]);
                    }

                    result = CrossPlayLiteral.ofArray(arr);
                    break;
                }
                case RC_SENSE_NEARBY_ROBOTS__INT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int rad = getLiteralValue(message.params[1]);
                    RobotInfo[] infos = rc.senseNearbyRobots(rad);
                    CrossPlayObject[] arr = new CrossPlayObject[infos.length];

                    for (int i = 0; i < infos.length; i++) {
                        arr[i] = setNextObject(ROBOT_INFO, infos[i]);
                    }

                    result = CrossPlayLiteral.ofArray(arr);
                    break;
                }
                case RC_SENSE_NEARBY_ROBOTS__INT_TEAM: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int rad = getLiteralValue(message.params[1]);
                    Team teamParam = getLiteralValue(message.params[2]);
                    RobotInfo[] infos = rc.senseNearbyRobots(rad, teamParam);
                    CrossPlayObject[] arr = new CrossPlayObject[infos.length];

                    for (int i = 0; i < infos.length; i++) {
                        arr[i] = setNextObject(ROBOT_INFO, infos[i]);
                    }

                    result = CrossPlayLiteral.ofArray(arr);
                    break;
                }
                case RC_SENSE_NEARBY_ROBOTS__LOC_INT_TEAM: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation center = this.<MapLocation>getObject(message.params[1]);
                    int rad = getLiteralValue(message.params[2]);
                    Team teamParam = getLiteralValue(message.params[3]);
                    RobotInfo[] infos = rc.senseNearbyRobots(center, rad, teamParam);
                    CrossPlayObject[] arr = new CrossPlayObject[infos.length];

                    for (int i = 0; i < infos.length; i++) {
                        arr[i] = setNextObject(ROBOT_INFO, infos[i]);
                    }

                    result = CrossPlayLiteral.ofArray(arr);
                    break;
                }
                case RC_SENSE_PASSABILITY: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.sensePassability(loc));
                    break;
                }
                case RC_SENSE_MAP_INFO: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    MapInfo mi = rc.senseMapInfo(loc);
                    result = CrossPlayLiteral.fromMapInfo(mi);
                    break;
                }
                case RC_SENSE_NEARBY_MAP_INFOS: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapInfo[] infos = rc.senseNearbyMapInfos();
                    result = CrossPlayLiteral.ofMapInfoArray(infos);
                    break;
                }
                case RC_SENSE_NEARBY_MAP_INFOS__INT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int rad = getLiteralValue(message.params[1]);
                    MapInfo[] infos = rc.senseNearbyMapInfos(rad);
                    result = CrossPlayLiteral.ofMapInfoArray(infos);
                    break;
                }
                case RC_SENSE_NEARBY_MAP_INFOS__LOC: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation center = this.<MapLocation>getObject(message.params[1]);
                    MapInfo[] infos = rc.senseNearbyMapInfos(center);
                    result = CrossPlayLiteral.ofMapInfoArray(infos);
                    break;
                }
                case RC_SENSE_NEARBY_MAP_INFOS__LOC_INT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation center = this.<MapLocation>getObject(message.params[1]);
                    int rad = getLiteralValue(message.params[2]);
                    MapInfo[] infos = rc.senseNearbyMapInfos(center, rad);
                    result = CrossPlayLiteral.ofMapInfoArray(infos);
                    break;
                }
                case RC_ADJACENT_LOCATION: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    Direction dir = this.<Direction>getObject(message.params[1]);
                    MapLocation adjLoc = rc.adjacentLocation(dir);
                    result = CrossPlayLiteral.ofMapLocation(adjLoc);
                    break;
                }
                case RC_GET_ALL_LOCATIONS_WITHIN_RADIUS_SQUARED: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation center = this.<MapLocation>getObject(message.params[1]);
                    int r2 = getLiteralValue(message.params[2]);
                    MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(center, r2);
                    CrossPlayObject[] arr = new CrossPlayObject[locs.length];

                    for (int i = 0; i < locs.length; i++) {
                        arr[i] = CrossPlayLiteral.ofMapLocation(locs[i]);
                    }

                    result = CrossPlayLiteral.ofArray(arr);
                    break;
                }
                case RC_IS_ACTION_READY:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.isActionReady());
                    break;
                case RC_GET_ACTION_COOLDOWN_TURNS:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getActionCooldownTurns());
                    break;
                case RC_IS_MOVEMENT_READY:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.isMovementReady());
                    break;
                case RC_IS_TURNING_READY:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.isTurningReady());
                    break;
                case RC_GET_MOVEMENT_COOLDOWN_TURNS:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getMovementCooldownTurns());
                    break;
                case RC_GET_TURNING_COOLDOWN_TURNS:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getTurningCooldownTurns());
                    break;
                case RC_CAN_MOVE_FORWARD:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.canMoveForward());
                    break;
                case RC_CAN_MOVE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    Direction d = this.<Direction>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canMove(d));
                    break;
                }
                case RC_MOVE_FORWARD:
                    rc = this.<RobotController>getObject(message.params[0]);
                    rc.moveForward();
                    result = CrossPlayLiteral.NULL;
                    break;
                case RC_MOVE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    Direction d = this.<Direction>getObject(message.params[1]);
                    rc.move(d);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_TURN:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.canTurn());
                    break;
                case RC_TURN: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    Direction d = this.<Direction>getObject(message.params[1]);
                    rc.turn(d);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_GET_CURRENT_RAT_COST:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofInt(rc.getCurrentRatCost());
                    break;
                case RC_CAN_BUILD_ROBOT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canBuildRobot(loc));
                    break;
                }
                case RC_BUILD_ROBOT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.buildRobot(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_BECOME_RAT_KING:
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.canBecomeRatKing());
                    break;
                case RC_BECOME_RAT_KING:
                    rc = this.<RobotController>getObject(message.params[0]);
                    rc.becomeRatKing();
                    result = CrossPlayLiteral.NULL;
                    break;
                case RC_CAN_PLACE_DIRT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canPlaceDirt(loc));
                    break;
                }
                case RC_PLACE_DIRT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.placeDirt(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_REMOVE_DIRT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canRemoveDirt(loc));
                    break;
                }
                case RC_REMOVE_DIRT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.removeDirt(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_PLACE_RAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canPlaceRatTrap(loc));
                    break;
                }
                case RC_PLACE_RAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.placeRatTrap(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_REMOVE_RAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canRemoveRatTrap(loc));
                    break;
                }
                case RC_REMOVE_RAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.removeRatTrap(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_PLACE_CAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canPlaceCatTrap(loc));
                    break;
                }
                case RC_PLACE_CAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.placeCatTrap(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_REMOVE_CAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canRemoveCatTrap(loc));
                    break;
                }
                case RC_REMOVE_CAT_TRAP: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.removeCatTrap(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_PICK_UP_CHEESE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canPickUpCheese(loc));
                    break;
                }
                case RC_PICK_UP_CHEESE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.pickUpCheese(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_ATTACK: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canAttack(loc));
                    break;
                }
                case RC_CAN_ATTACK__LOC_INT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    int i = getLiteralValue(message.params[2]);
                    result = CrossPlayLiteral.ofBoolean(rc.canAttack(loc, i));
                    break;
                }
                case RC_ATTACK: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    rc.attack(loc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_ATTACK__LOC_INT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation loc = this.<MapLocation>getObject(message.params[1]);
                    int i = getLiteralValue(message.params[2]);
                    rc.attack(loc, i);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_SQUEAK: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int msg = getLiteralValue(message.params[1]);
                    rc.squeak(msg);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_READ_SQUEAKS: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int roundNumParam = getLiteralValue(message.params[1]);
                    Message[] msgs = rc.readSqueaks(roundNumParam);
                    CrossPlayObject[] marr = new CrossPlayObject[msgs.length];

                    for (int i = 0; i < msgs.length; i++) {
                        marr[i] = setNextObject(MESSAGE, msgs[i]);
                    }

                    result = CrossPlayLiteral.ofArray(marr);
                    break;
                }
                case RC_WRITE_SHARED_ARRAY: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int idx = getLiteralValue(message.params[1]);
                    int val = getLiteralValue(message.params[2]);
                    rc.writeSharedArray(idx, val);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_READ_SHARED_ARRAY: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int idx = getLiteralValue(message.params[1]);
                    int v = rc.readSharedArray(idx);
                    result = CrossPlayLiteral.ofInt(v);
                    break;
                }
                case RC_WRITE_PERSISTENT_ARRAY: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int idx = getLiteralValue(message.params[1]);
                    int val = getLiteralValue(message.params[2]);
                    rc.writePersistentArray(idx, val);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_READ_PERSISTENT_ARRAY: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    int idx = getLiteralValue(message.params[1]);
                    int v = rc.readPersistentArray(idx);
                    result = CrossPlayLiteral.ofInt(v);
                    break;
                }
                case RC_CAN_TRANSFER_CHEESE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation locCheese = this.<MapLocation>getObject(message.params[1]);
                    int amt = getLiteralValue(message.params[2]);
                    result = CrossPlayLiteral.ofBoolean(rc.canTransferCheese(locCheese, amt));
                    break;
                }
                case RC_TRANSFER_CHEESE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation locCheese = this.<MapLocation>getObject(message.params[1]);
                    int amt = getLiteralValue(message.params[2]);
                    rc.transferCheese(locCheese, amt);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_THROW_RAT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    rc.throwRat();
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_THROW_RAT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    result = CrossPlayLiteral.ofBoolean(rc.canThrowRat());
                    break;
                }
                case RC_DROP_RAT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    Direction dropDir = this.<Direction>getObject(message.params[1]);
                    rc.dropRat(dropDir);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_CAN_DROP_RAT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    Direction dropDir = this.<Direction>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canDropRat(dropDir));
                    break;
                }
                case RC_CAN_CARRY_RAT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation carryLoc = this.<MapLocation>getObject(message.params[1]);
                    result = CrossPlayLiteral.ofBoolean(rc.canCarryRat(carryLoc));
                    break;
                }
                case RC_CARRY_RAT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation carryLoc = this.<MapLocation>getObject(message.params[1]);
                    rc.carryRat(carryLoc);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_DISINTEGRATE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    rc.disintegrate();
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_RESIGN: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    rc.resign();
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_SET_INDICATOR_STRING: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    String s = getLiteralValue(message.params[1]);
                    rc.setIndicatorString(s);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_SET_INDICATOR_DOT: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation locDot = this.<MapLocation>getObject(message.params[1]);
                    int r = getLiteralValue(message.params[2]);
                    int g = getLiteralValue(message.params[3]);
                    int b = getLiteralValue(message.params[4]);
                    rc.setIndicatorDot(locDot, r, g, b);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_SET_INDICATOR_LINE: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    MapLocation sLoc = this.<MapLocation>getObject(message.params[1]);
                    MapLocation eLoc = this.<MapLocation>getObject(message.params[2]);
                    int r = getLiteralValue(message.params[3]);
                    int g = getLiteralValue(message.params[4]);
                    int b = getLiteralValue(message.params[5]);
                    rc.setIndicatorLine(sLoc, eLoc, r, g, b);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case RC_SET_TIMELINE_MARKER: {
                    rc = this.<RobotController>getObject(message.params[0]);
                    String label = getLiteralValue(message.params[1]);
                    int r = getLiteralValue(message.params[2]);
                    int g = getLiteralValue(message.params[3]);
                    int b = getLiteralValue(message.params[4]);
                    rc.setTimelineMarker(label, r, g, b);
                    result = CrossPlayLiteral.NULL;
                    break;
                }
                case LOG: {
                    // System.out.println("Processing LOG");
                    String msg = getLiteralValue(message.params[0]);

                    if (this.out instanceof RoboPrintStream rps) {
                        rps.println(msg);
                    }

                    result = CrossPlayLiteral.NULL;
                    break;
                }
                default:
                    throw new CrossPlayException("Received unknown cross-play method: " + message.method);
            }
        } catch (GameActionException e) {
            result = new CrossPlayLiteral(THROWN_GAME_ACTION_EXCEPTION, e);
        }

        return result;
    }

    public int playTurn(RobotController rc, OutputStream systemOut) {
        // System.out.println("playTurn called for CrossPlay instance " + this.hashCode());

        if (this.rc == rc && this.roundNum == rc.getRoundNum()) {
            // System.out.println("playTurn returned early for CrossPlay instance " + this.hashCode());
            return 0;
        }

        this.rc = rc;
        this.roundNum = rc.getRoundNum();
        this.team = rc.getTeam();
        this.id = rc.getID();
        this.out = systemOut;

        if (this.initialized) {
            this.rcRef = new CrossPlayReference(ROBOT_CONTROLLER, 0);
        } else {
            clearObjects();
            this.rcRef = setNextObject(ROBOT_CONTROLLER, this.rc);
            this.initialized = true;
            // System.out.println("Cross-play bot initialized!");
        }

        // System.out.println("Running message passing for CrossPlay instance " + this.hashCode());
        int bytecodeUsed = runMessagePassing();
        // System.out.println("playTurn finished for CrossPlay instance " + this.hashCode());
        return bytecodeUsed;
    }
}
