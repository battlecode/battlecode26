package battlecode.crossplay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import battlecode.common.*;
import battlecode.instrumenter.stream.RoboPrintStream;
import battlecode.server.Server;

import static battlecode.crossplay.CrossPlayHelpers.*;
import static battlecode.crossplay.CrossPlayObjectType.THROWN_GAME_ACTION_EXCEPTION;

/**
 * Allows bots written in different languages to be run by the Java engine using a message-passing system.
 * Battlecode 2026 supports Java and Python.
 */
public class CrossPlay {
    private static final int IPC_PORT = 27185;

    private RobotController processingRobot;
    private OutputStream out;
    private ServerSocket serverSocket;
    private Socket socket;
    private DataInputStream socketIn;
    private DataOutputStream socketOut;
    private GameActionException recentException;
    private Set<Integer> initializedBots;

    private final ObjectMapper objectMapper;

    public CrossPlay() {
        this.objectMapper = new ObjectMapper();
        this.initializedBots = new java.util.HashSet<>();
    }

    private void initSocket() {
        if (socket != null) return;

        Server.debug("Init crossplay socket");
        try {
            serverSocket = new ServerSocket(IPC_PORT);
            socket = serverSocket.accept();

            Server.debug("Crossplay connection accepted");

            socketIn = new DataInputStream(socket.getInputStream());
            socketOut = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            throw new CrossPlayException("Failed to initialize CrossPlay socket IPC: " + e.toString());
        }
    }

    private void sendJson(JsonNode json) throws IOException {
        Server.debug("Sending json: " + json.toString());
        initSocket();
        byte[] bytes = objectMapper.writeValueAsBytes(json);
        socketOut.writeInt(bytes.length);
        socketOut.write(bytes);
        socketOut.flush();
    }

    private JsonNode receiveJson() throws IOException {
        initSocket();
        int len = socketIn.readInt();
        byte[] bytes = socketIn.readNBytes(len);
        return objectMapper.readTree(bytes);
    }

    private void sendJsonAndReceiveNull(JsonNode json) throws IOException, CrossPlayException {
        sendJson(json);
        JsonNode response = receiveJson();

        if (!response.isNull()) {
            throw new CrossPlayException("Expected null response, got: " + response.toString());
        }
    }

    public void cleanup() {
        try {
            sendEndGame();
        } catch (Exception e) {}
        
        try {
            if (socket != null) socket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    public void setPrintStream(OutputStream out) {
        this.out = out;
    }

    public int runMessagePassing(boolean init) throws CrossPlayException, RethrownGameActionException, NonJavaBotException {
        while (true) {
            try {
                JsonNode messageJson = receiveJson();
                CrossPlayMessage message = objectMapper.treeToValue(messageJson, CrossPlayMessage.class);

                JsonNode result = processMessage(message, init);
                sendJson(result);

                if (message.method() == CrossPlayMethod.END_TURN) {
                    if (message.params() != null && message.params().isArray() && message.params().size() > 0) {
                        return message.params().get(0).asInt();
                    } else {
                        return 0;
                    }
                }
            } catch (JsonProcessingException e) {
                throw new CrossPlayException("Protocol error: Invalid JSON received from client: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                throw new CrossPlayException("Protocol error: Failed to map JSON to CrossPlayMessage: " + e.getMessage());
            } catch (IOException e) {
                throw new CrossPlayException("Socket IPC failed: " + e.toString());
            } catch (RethrownGameActionException e) {
                throw e;
            } catch (GameActionException e) {
                sendException(e);
            }
        }
    }

    private JsonNode processMessage(CrossPlayMessage message, boolean init) throws GameActionException, NonJavaBotException {
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        CrossPlayMethod method = message.method();

        if (init && !method.isValidInitMethod()) {
            throw new GameActionException(GameActionExceptionType.CANT_DO_THAT, "Python bot attempted to call method " + method + " during initialization!"
                + " This method can only be called in the turn() function.");
        }

        switch (method) {
            case INVALID: {
                throw new CrossPlayException("Received invalid cross-play method!");
            }

            case END_TURN: {
                return nodeFactory.nullNode();
            }

            case LOG: {
                checkParams(message, 1);
                String msg = message.params().get(0).asText();

                if (this.out instanceof RoboPrintStream rps) {
                        rps.println(msg);
                }

                return nodeFactory.nullNode();
            }

            case RC_GET_ROUND_NUM: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getRoundNum());
            }

            case RC_GET_MAP_WIDTH: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getMapWidth());
            }

            case RC_GET_MAP_HEIGHT: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getMapHeight());
            }

            case RC_ADJACENT_LOCATION: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                return makeLocNode(nodeFactory, this.processingRobot.adjacentLocation(dir));
            }

            case RC_ATTACK: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.attack(loc);
                return nodeFactory.nullNode();
            }

            case RC_ATTACK__LOC_INT: {
                checkParams(message, 2);
                MapLocation loc = parseLocNode(message.params().get(0));
                int cheeseAmount = message.params().get(1).asInt();
                this.processingRobot.attack(loc, cheeseAmount);
                return nodeFactory.nullNode();
            }

            case RC_BECOME_RAT_KING: {
                checkParams(message, 0);
                this.processingRobot.becomeRatKing();
                return nodeFactory.nullNode();
            }

            case RC_BUILD_RAT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.buildRat(loc);
                return nodeFactory.nullNode();
            }

            case RC_CAN_ATTACK: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canAttack(loc));
            }

            case RC_CAN_ATTACK__LOC_INT: {
                checkParams(message, 2);
                MapLocation loc = parseLocNode(message.params().get(0));
                int cheeseAmount = message.params().get(1).asInt();
                return nodeFactory.booleanNode(this.processingRobot.canAttack(loc, cheeseAmount));
            }

            case RC_CAN_BECOME_RAT_KING: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.canBecomeRatKing());
            }

            case RC_CAN_BUILD_RAT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canBuildRat(loc));
            }

            case RC_CAN_CARRY_RAT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canCarryRat(loc));
            }

            case RC_CAN_DROP_RAT: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canDropRat(dir));
            }

            case RC_CAN_MOVE: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canMove(dir));
            }

            case RC_CAN_MOVE_FORWARD: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.canMoveForward());
            }

            case RC_CAN_PICK_UP_CHEESE: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canPickUpCheese(loc));
            }

            case RC_CAN_PLACE_CAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canPlaceCatTrap(loc));
            }

            case RC_CAN_PLACE_DIRT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canPlaceDirt(loc));
            }

            case RC_CAN_PLACE_RAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canPlaceRatTrap(loc));
            }

            case RC_CAN_REMOVE_CAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canRemoveCatTrap(loc));
            }

            case RC_CAN_REMOVE_DIRT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canRemoveDirt(loc));
            }

            case RC_CAN_REMOVE_RAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canRemoveRatTrap(loc));
            }

            case RC_CAN_SENSE_LOCATION: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canSenseLocation(loc));

            }
            case RC_CAN_SENSE_ROBOT: {
                checkParams(message, 1);
                int id = message.params().get(0).asInt();
                return nodeFactory.booleanNode(this.processingRobot.canSenseRobot(id));
            }

            case RC_CAN_SENSE_ROBOT_AT_LOCATION: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canSenseRobotAtLocation(loc));
            }

            case RC_CAN_THROW_RAT: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.canThrowRat());
            }

            case RC_CAN_TRANSFER_CHEESE: {
                checkParams(message, 2);
                MapLocation loc = parseLocNode(message.params().get(0));
                int cheeseAmount = message.params().get(1).asInt();
                return nodeFactory.booleanNode(this.processingRobot.canTransferCheese(loc, cheeseAmount));
            }

            case RC_CAN_TURN: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.canTurn());
            }

            case RC_CAN_TURN__DIR: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.canTurn(dir));
            }

            case RC_CARRY_RAT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.carryRat(loc);
                return nodeFactory.nullNode();
            }

            case RC_DISINTEGRATE: {
                checkParams(message, 0);
                this.processingRobot.disintegrate();
                return nodeFactory.nullNode();
            }

            case RC_DROP_RAT: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                this.processingRobot.dropRat(dir);
                return nodeFactory.nullNode();
            }

            case RC_GET_ACTION_COOLDOWN_TURNS: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getActionCooldownTurns());
            }

            case RC_GET_ALL_CHEESE: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getAllCheese());
            }

            case RC_GET_ALL_LOCATIONS_WITHIN_RADIUS_SQUARED: {
                checkParams(message, 2);
                MapLocation center = parseLocNode(message.params().get(0));
                int radiusSquared = message.params().get(1).asInt();
                MapLocation[] locations = this.processingRobot.getAllLocationsWithinRadiusSquared(center, radiusSquared);
                return makeArrayNode(nodeFactory, locations, CrossPlayHelpers::makeLocNode);
            }

            case RC_GET_ALL_PART_LOCATIONS: {
                checkParams(message, 0);
                MapLocation[] locations = this.processingRobot.getAllPartLocations();
                return makeArrayNode(nodeFactory, locations, CrossPlayHelpers::makeLocNode);
            }

            case RC_GET_CARRYING: {
                checkParams(message, 0);
                return makeRobotInfoNode(nodeFactory, this.processingRobot.getCarrying());
            }

            case RC_GET_CURRENT_RAT_COST: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getCurrentRatCost());
            }

            case RC_GET_DIRECTION: {
                checkParams(message, 0);
                return makeDirNode(nodeFactory, this.processingRobot.getDirection());
            }

            case RC_GET_DIRT: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getDirt());
            }

            case RC_GET_GLOBAL_CHEESE: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getGlobalCheese());
            }

            case RC_GET_HEALTH: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getHealth());
            }

            case RC_GET_ID: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getID());
            }

            case RC_GET_LOCATION: {
                checkParams(message, 0);
                return makeLocNode(nodeFactory, this.processingRobot.getLocation());
            }

            case RC_GET_MOVEMENT_COOLDOWN_TURNS: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getMovementCooldownTurns());
            }

            case RC_GET_RAW_CHEESE: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getRawCheese());
            }

            case RC_GET_TEAM: {
                checkParams(message, 0);
                return makeTeamNode(nodeFactory, this.processingRobot.getTeam());
            }

            case RC_GET_TURNING_COOLDOWN_TURNS: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getTurningCooldownTurns());
            }

            case RC_GET_TYPE: {
                checkParams(message, 0);
                return makeUnitTypeNode(nodeFactory, this.processingRobot.getType());
            }

            case RC_IS_ACTION_READY: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.isActionReady());
            }

            case RC_IS_BEING_CARRIED: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.isBeingCarried());
            }

            case RC_IS_BEING_THROWN: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.isBeingThrown());
            }

            case RC_IS_COOPERATION: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.isCooperation());
            }

            case RC_IS_LOCATION_OCCUPIED: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.isLocationOccupied(loc));
            }

            case RC_IS_MOVEMENT_READY: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.isMovementReady());
            }

            case RC_IS_TURNING_READY: {
                checkParams(message, 0);
                return nodeFactory.booleanNode(this.processingRobot.isTurningReady());
            }

            case RC_MOVE: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                this.processingRobot.move(dir);
                return nodeFactory.nullNode();
            }

            case RC_MOVE_FORWARD: {
                checkParams(message, 0);
                this.processingRobot.moveForward();
                return nodeFactory.nullNode();
            }

            case RC_ON_THE_MAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.onTheMap(loc));
            }

            case RC_PICK_UP_CHEESE: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.pickUpCheese(loc);
                return nodeFactory.nullNode();
            }

            case RC_PICK_UP_CHEESE__LOC_INT: {
                checkParams(message, 2);
                MapLocation loc = parseLocNode(message.params().get(0));
                int cheeseAmount = message.params().get(1).asInt();
                this.processingRobot.pickUpCheese(loc, cheeseAmount);
                return nodeFactory.nullNode();
            }

            case RC_PLACE_CAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.placeCatTrap(loc);
                return nodeFactory.nullNode();
            }

            case RC_PLACE_DIRT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.placeDirt(loc);
                return nodeFactory.nullNode();
            }

            case RC_PLACE_RAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.placeRatTrap(loc);
                return nodeFactory.nullNode();
            }

            case RC_READ_SHARED_ARRAY: {
                checkParams(message, 1);
                int index = message.params().get(0).asInt();
                return nodeFactory.numberNode(this.processingRobot.readSharedArray(index));
            }

            case RC_READ_SQUEAKS: {
                checkParams(message, 1);
                int roundNum = message.params().get(0).asInt();
                Message[] squeaks = this.processingRobot.readSqueaks(roundNum);
                return makeArrayNode(nodeFactory, squeaks, CrossPlayHelpers::makeMessageNode);
            }

            case RC_REMOVE_CAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.removeCatTrap(loc);
                return nodeFactory.nullNode();
            }

            case RC_REMOVE_DIRT: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.removeDirt(loc);
                return nodeFactory.nullNode();
            }

            case RC_REMOVE_RAT_TRAP: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                this.processingRobot.removeRatTrap(loc);
                return nodeFactory.nullNode();
            }

            case RC_RESIGN: {
                checkParams(message, 0);
                this.processingRobot.resign();
                return nodeFactory.nullNode();
            }

            case RC_SENSE_MAP_INFO: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                MapInfo mapInfo = this.processingRobot.senseMapInfo(loc);
                return makeMapInfoNode(nodeFactory, mapInfo);
            }

            case RC_SENSE_NEARBY_MAP_INFOS: {
                checkParams(message, 0);
                MapInfo[] mapInfos = this.processingRobot.senseNearbyMapInfos();
                return makeArrayNode(nodeFactory, mapInfos, CrossPlayHelpers::makeMapInfoNode);
            }

            case RC_SENSE_NEARBY_MAP_INFOS__INT: {
                checkParams(message, 1);
                int radiusSquared = message.params().get(0).asInt();
                MapInfo[] mapInfos = this.processingRobot.senseNearbyMapInfos(radiusSquared);
                return makeArrayNode(nodeFactory, mapInfos, CrossPlayHelpers::makeMapInfoNode);
            }

            case RC_SENSE_NEARBY_MAP_INFOS__LOC: {
                checkParams(message, 1);
                MapLocation center = parseLocNode(message.params().get(0));
                MapInfo[] mapInfos = this.processingRobot.senseNearbyMapInfos(center);
                return makeArrayNode(nodeFactory, mapInfos, CrossPlayHelpers::makeMapInfoNode);
            }

            case RC_SENSE_NEARBY_MAP_INFOS__LOC_INT: {
                checkParams(message, 2);
                MapLocation center = parseLocNode(message.params().get(0));
                int radiusSquared = message.params().get(1).asInt();
                MapInfo[] mapInfos = this.processingRobot.senseNearbyMapInfos(center, radiusSquared);
                return makeArrayNode(nodeFactory, mapInfos, CrossPlayHelpers::makeMapInfoNode);
            }

            case RC_SENSE_NEARBY_ROBOTS: {
                checkParams(message, 0);
                RobotInfo[] robotInfos = this.processingRobot.senseNearbyRobots();
                return makeArrayNode(nodeFactory, robotInfos, CrossPlayHelpers::makeRobotInfoNode);
            }

            case RC_SENSE_NEARBY_ROBOTS__INT: {
                checkParams(message, 1);
                int radiusSquared = message.params().get(0).asInt();
                RobotInfo[] robotInfos = this.processingRobot.senseNearbyRobots(radiusSquared);
                return makeArrayNode(nodeFactory, robotInfos, CrossPlayHelpers::makeRobotInfoNode);
            }

            case RC_SENSE_NEARBY_ROBOTS__INT_TEAM: {
                checkParams(message, 2);
                int radiusSquared = message.params().get(0).asInt();
                Team team = parseTeamNode(message.params().get(1));
                RobotInfo[] robotInfos = this.processingRobot.senseNearbyRobots(radiusSquared, team);
                return makeArrayNode(nodeFactory, robotInfos, CrossPlayHelpers::makeRobotInfoNode);
            }

            case RC_SENSE_NEARBY_ROBOTS__LOC_INT_TEAM: {
                checkParams(message, 3);
                MapLocation center = parseLocNode(message.params().get(0));
                int radiusSquared = message.params().get(1).asInt();
                Team team = parseTeamNode(message.params().get(2));
                RobotInfo[] robotInfos = this.processingRobot.senseNearbyRobots(center, radiusSquared, team);
                return makeArrayNode(nodeFactory, robotInfos, CrossPlayHelpers::makeRobotInfoNode);
            }

            case RC_SENSE_PASSABILITY: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return nodeFactory.booleanNode(this.processingRobot.sensePassability(loc));
            }

            case RC_SENSE_ROBOT: {
                checkParams(message, 1);
                int id = message.params().get(0).asInt();
                return makeRobotInfoNode(nodeFactory, this.processingRobot.senseRobot(id));
            }

            case RC_SENSE_ROBOT_AT_LOCATION: {
                checkParams(message, 1);
                MapLocation loc = parseLocNode(message.params().get(0));
                return makeRobotInfoNode(nodeFactory, this.processingRobot.senseRobotAtLocation(loc));
            }

            case RC_SET_INDICATOR_DOT: {
                checkParams(message, 3);
                MapLocation loc = parseLocNode(message.params().get(0));
                int r = message.params().get(1).asInt();
                int g = message.params().get(2).asInt();
                int b = message.params().get(3).asInt();
                this.processingRobot.setIndicatorDot(loc, r, g, b);
                return nodeFactory.nullNode();
            }

            case RC_SET_INDICATOR_LINE: {
                checkParams(message, 5);
                MapLocation startLoc = parseLocNode(message.params().get(0));
                MapLocation endLoc = parseLocNode(message.params().get(1));
                int r = message.params().get(2).asInt();
                int g = message.params().get(3).asInt();
                int b = message.params().get(4).asInt();
                this.processingRobot.setIndicatorLine(startLoc, endLoc, r, g, b);
                return nodeFactory.nullNode();
            }

            case RC_SET_INDICATOR_STRING: {
                checkParams(message, 1);
                String text = message.params().get(0).asText();
                this.processingRobot.setIndicatorString(text);
                return nodeFactory.nullNode();
            }

            case RC_SET_TIMELINE_MARKER: {
                checkParams(message, 4);
                String text = message.params().get(0).asText();
                int r = message.params().get(1).asInt();
                int g = message.params().get(2).asInt();
                int b = message.params().get(3).asInt();
                this.processingRobot.setTimelineMarker(text, r, g, b);
                return nodeFactory.nullNode();
            }

            case RC_SQUEAK: {
                checkParams(message, 1);
                int squeak = message.params().get(0).asInt();
                this.processingRobot.squeak(squeak);
                return nodeFactory.nullNode();
            }

            case RC_THROW_RAT: {
                checkParams(message, 0);
                this.processingRobot.throwRat();
                return nodeFactory.nullNode();
            }

            case RC_TRANSFER_CHEESE: {
                checkParams(message, 2);
                MapLocation loc = parseLocNode(message.params().get(0));
                int cheeseAmount = message.params().get(1).asInt();
                this.processingRobot.transferCheese(loc, cheeseAmount);
                return nodeFactory.nullNode();
            }

            case RC_TURN: {
                checkParams(message, 1);
                Direction dir = parseDirNode(message.params().get(0));
                this.processingRobot.turn(dir);
                return nodeFactory.nullNode();
            }

            case RC_WRITE_SHARED_ARRAY: {
                checkParams(message, 2);
                int index = message.params().get(0).asInt();
                int value = message.params().get(1).asInt();
                this.processingRobot.writeSharedArray(index, value);
                return nodeFactory.nullNode();
            }

            case RC_GET_BACKSTABBING_TEAM: {
                checkParams(message, 0);
                Team backstabTeam = this.processingRobot.getBackstabbingTeam();
                return makeTeamNode(nodeFactory, backstabTeam);
            }

            case RC_GET_NUMBER_RAT_TRAPS: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getNumberRatTraps());
            }

            case RC_GET_NUMBER_CAT_TRAPS: {
                checkParams(message, 0);
                return nodeFactory.numberNode(this.processingRobot.getNumberCatTraps());
            }

            case ML_BOTTOM_LEFT_DISTANCE_SQUARED_TO: {
                checkParams(message, 2);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                return nodeFactory.numberNode(loc1.bottomLeftDistanceSquaredTo(loc2));
            }

            case ML_DIRECTION_TO: {
                checkParams(message, 2);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                return makeDirNode(nodeFactory, loc1.directionTo(loc2));
            }

            case ML_DISTANCE_SQUARED_TO: {
                checkParams(message, 2);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                return nodeFactory.numberNode(loc1.distanceSquaredTo(loc2));
            }

            case ML_IS_ADJACENT_TO: {
                checkParams(message, 2);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                return nodeFactory.booleanNode(loc1.isAdjacentTo(loc2));
            }

            case ML_IS_WITHIN_DISTANCE_SQUARED: {
                checkParams(message, 3);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                int distanceSquared = message.params().get(2).asInt();
                return nodeFactory.booleanNode(loc1.isWithinDistanceSquared(loc2, distanceSquared));
            }

            case ML_IS_WITHIN_DISTANCE_SQUARED__LOC_INT_DIR_DOUBLE: {
                checkParams(message, 5);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                int distanceSquared = message.params().get(2).asInt();
                Direction dir = parseDirNode(message.params().get(3));
                double theta = message.params().get(4).asDouble();
                return nodeFactory.booleanNode(loc1.isWithinDistanceSquared(loc2, distanceSquared, dir, theta));
            }

            case ML_IS_WITHIN_DISTANCE_SQUARED__LOC_INT_DIR_DOUBLE_BOOLEAN: {
                checkParams(message, 6);
                MapLocation loc1 = parseLocNode(message.params().get(0));
                MapLocation loc2 = parseLocNode(message.params().get(1));
                int distanceSquared = message.params().get(2).asInt();
                Direction dir = parseDirNode(message.params().get(3));
                double theta = message.params().get(4).asDouble();
                boolean useBottomLeft = message.params().get(5).asBoolean();
                return nodeFactory.booleanNode(loc1.isWithinDistanceSquared(loc2, distanceSquared, dir, theta, useBottomLeft));
            }

            case UT_GET_ALL_TYPE_LOCATIONS: {
                checkParams(message, 2);
                UnitType type = parseUnitTypeNode(message.params().get(0));
                MapLocation center = parseLocNode(message.params().get(1));
                MapLocation[] locations = type.getAllTypeLocations(center);
                return makeArrayNode(nodeFactory, locations, CrossPlayHelpers::makeLocNode);
            }

            case THROW_GAME_ACTION_EXCEPTION: {
                checkParams(message, 2);
                GameActionExceptionType etype = gameActionExceptionTypes[message.params().get(0).asInt()];
                String traceback = message.params().get(1).asText();
                throw new RethrownGameActionException(etype, traceback, recentException);
            }

            case THROW_EXCEPTION: {
                checkParams(message, 1);
                String traceback = message.params().get(0).asText();
                throw new NonJavaBotException(traceback);
            }

            default: {
                throw new CrossPlayException("Received unknown cross-play method: " + message.method());
            }
        }
    }

    private void sendStartTurn() {
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        ObjectNode startTurn = nodeFactory.objectNode();
        startTurn.put("type", "start_turn");
        startTurn.put("round", this.processingRobot.getRoundNum());
        startTurn.set("team", makeTeamNode(nodeFactory, this.processingRobot.getTeam()));
        startTurn.put("id", this.processingRobot.getID());

        try {
            sendJson(startTurn);
        } catch (IOException e) {
             throw new CrossPlayException("Failed to send start turn: " + e.toString());
        }
    }

    public void sendException(GameActionException e) {
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        ObjectNode exceptionNode = nodeFactory.objectNode();
        exceptionNode.put("type", THROWN_GAME_ACTION_EXCEPTION.ordinal());
        exceptionNode.put("etype", e.getType().ordinal());
        exceptionNode.put("msg", e.getMessage());

        try {
            sendJson(exceptionNode);
        } catch (IOException e2) {
            throw new CrossPlayException("Failed to send GameActionException over the socket: " + e2.toString());
        }
    }

    public void sendSpawnBot(RobotController rc) {
        this.processingRobot = rc;
        int id = rc.getID();
        Team team = rc.getTeam();
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        ObjectNode spawnBot = nodeFactory.objectNode();
        spawnBot.put("type", "spawn_bot");
        spawnBot.put("id", id);
        spawnBot.put("team", team.ordinal());

        try {
            sendJsonAndReceiveNull(spawnBot);
            runMessagePassing(true);
            // TODO make this check bytecode by using runMessagePassing's return value
        } catch (IOException e) {
            throw new CrossPlayException("Failed to send 'spawn bot': " + e.toString());
        } catch (NonJavaBotException e) {
            printException(team, id, rc.getRoundNum(), e);
        } catch (RethrownGameActionException e) {
            printException(team, id, rc.getRoundNum(), e);
        }
    }

    public void printException(Team team, int id, int round, Exception e) {
        String message = processPythonTraceback(e.getMessage());
        String teamStr = team.toString();
        String idStr = Integer.toString(id);
        String formattedMessage = String.format("[%s: #%s@%s] ERROR:\n\n%s\n", teamStr, idStr, round, message);
        System.out.println(formattedMessage);
    }

    private String processPythonTraceback(String traceback) {
        String[] lines = traceback.split("\\r?\\n");
        String prefix = "File \"<string>\", line "; // python-specific
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        sb.append("Traceback (most recent call last):");

        for (String line : lines) {
            int idx = 0;

            while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) {
                idx++;
            }
            
            if (idx < line.length() && line.startsWith(prefix, idx)) {
                sb.append("\n  File \"bot.py\", line ");
                sb.append(line.substring(idx + prefix.length()));
                found = true;
            }
        }

        sb.append("\n");
        sb.append(lines[lines.length - 1]);
        return found ? sb.toString() : traceback;
    }

    public void sendDestroyBot(int id) {
        // If socket isn't initialized, we can't send destroy message, but that's fine 
        // because it means no bots have been spawned or connected yet.
        if (socket == null) return;
        
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        ObjectNode destroyBot = nodeFactory.objectNode();
        destroyBot.put("type", "destroy_bot");
        destroyBot.put("id", id);
        
        try {
            sendJsonAndReceiveNull(destroyBot);
        } catch (IOException e) {
            throw new CrossPlayException("Failed to send 'destroy bot': " + e.toString());
        }
    }
    
    public void sendEndGame() {
        if (socket == null) return;
        
        JsonNodeFactory nodeFactory = objectMapper.getNodeFactory();
        ObjectNode endGame = nodeFactory.objectNode();
        endGame.put("type", "end_game");
        
        try {
            sendJson(endGame);
        } catch (IOException e) {
            // Ignore errors during cleanup
        }
    }

    public int playTurn(RobotController rc, OutputStream systemOut) throws RethrownGameActionException, NonJavaBotException {
        this.processingRobot = rc;
        this.out = systemOut;
        int id = rc.getID();

        if (!initializedBots.contains(id)) {
            sendSpawnBot(rc);
            initializedBots.add(id);
        }

        // Send start turn message to prime the client
        sendStartTurn();
        return runMessagePassing(false);
    }
}
