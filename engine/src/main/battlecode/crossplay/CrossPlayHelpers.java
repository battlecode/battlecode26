package battlecode.crossplay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import battlecode.common.*;

import static battlecode.crossplay.CrossPlayObjectType.*;

public class CrossPlayHelpers {
    @FunctionalInterface
    public interface Function2<A, B, C> {
        C apply(A a, B b);
    }

    private static final Direction[] directions = Direction.values();
    private static final Team[] teams = Team.values();
    private static final TrapType[] trapTypes = TrapType.values();
    private static final UnitType[] unitTypes = UnitType.values();
    public static final GameActionExceptionType[] gameActionExceptionTypes = GameActionExceptionType.values();

    public static void checkParams(CrossPlayMessage message, int expected) {
        if (message.params() == null || !message.params().isArray() || message.params().size() != expected) {
            throw new CrossPlayException("Invalid number of parameters for method " + message.method() +
                    ": expected " + expected + ", got " +
                    (message.params() == null ? 0 : message.params().size()));
        }
    }

    public static JsonNode makeLocNode(JsonNodeFactory nodeFactory, MapLocation loc) {
        if (loc == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode locNode = nodeFactory.objectNode();
        locNode.put("type", MAP_LOCATION.ordinal());
        locNode.put("x", loc.x);
        locNode.put("y", loc.y);
        return locNode;
    }

    public static MapLocation parseLocNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int x = node.get("x").asInt();
        int y = node.get("y").asInt();
        return new MapLocation(x, y);
    }

    public static JsonNode makeDirNode(JsonNodeFactory nodeFactory, Direction dir) {
        if (dir == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode dirNode = nodeFactory.objectNode();
        dirNode.put("type", DIRECTION.ordinal());
        dirNode.put("val", dir.ordinal());
        return dirNode;
    }

    public static Direction parseDirNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int val = node.get("val").asInt();
        return directions[val];
    }

    public static JsonNode makeTeamNode(JsonNodeFactory nodeFactory, Team team) {
        if (team == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode teamNode = nodeFactory.objectNode();
        teamNode.put("type", TEAM.ordinal());
        teamNode.put("val", team.ordinal());
        return teamNode;
    }

    public static Team parseTeamNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int val = node.get("val").asInt();
        return teams[val];
    }

    public static JsonNode makeUnitTypeNode(JsonNodeFactory nodeFactory, UnitType unitType) {
        if (unitType == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode unitTypeNode = nodeFactory.objectNode();
        unitTypeNode.put("type", UNIT_TYPE.ordinal());
        unitTypeNode.put("val", unitType.ordinal());
        return unitTypeNode;
    }

    public static UnitType parseUnitTypeNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int val = node.get("val").asInt();
        return unitTypes[val];
    }

    public static JsonNode makeTrapTypeNode(JsonNodeFactory nodeFactory, TrapType trapType) {
        if (trapType == null) {
            return null;
        }

        ObjectNode trapTypeNode = nodeFactory.objectNode();
        trapTypeNode.put("type", TRAP_TYPE.ordinal());
        trapTypeNode.put("val", trapType.ordinal());
        return trapTypeNode;
    }

    public static TrapType parseTrapTypeNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int val = node.get("val").asInt();
        return trapTypes[val];
    }

    public static JsonNode makeRobotInfoNode(JsonNodeFactory nodeFactory, RobotInfo robotInfo) {
        if (robotInfo == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode robotNode = nodeFactory.objectNode();
        robotNode.put("type", ROBOT_INFO.ordinal());
        robotNode.put("id", robotInfo.getID());
        robotNode.set("team", makeTeamNode(nodeFactory, robotInfo.getTeam()));
        robotNode.set("loc", makeLocNode(nodeFactory, robotInfo.getLocation()));
        robotNode.set("dir", makeDirNode(nodeFactory, robotInfo.getDirection()));
        robotNode.put("chir", robotInfo.getChirality());
        robotNode.put("hp", robotInfo.getHealth());
        robotNode.set("ut", makeUnitTypeNode(nodeFactory, robotInfo.getType()));
        robotNode.put("ch", robotInfo.getRawCheeseAmount());
        robotNode.set("carry", makeRobotInfoNode(nodeFactory, robotInfo.getCarryingRobot()));
        return robotNode;
    }

    public static RobotInfo parseRobotInfoNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int id = node.get("id").asInt();
        Team team = teams[node.get("team").asInt()];
        MapLocation loc = parseLocNode(node.get("loc"));
        Direction dir = parseDirNode(node.get("dir"));
        int chir = node.get("chir").asInt();
        int hp = node.get("hp").asInt();
        UnitType type = unitTypes[node.get("ut").asInt()];
        int cheese = node.get("ch").asInt();
        RobotInfo carrying = parseRobotInfoNode(node.get("carry"));
        return new RobotInfo(id, team, type, hp, loc, dir, chir, cheese, carrying);
    }

    public static JsonNode makeMapInfoNode(JsonNodeFactory nodeFactory, MapInfo mapInfo) {
        if (mapInfo == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode mapNode = nodeFactory.objectNode();
        mapNode.put("type", MAP_INFO.ordinal());
        mapNode.set("loc", makeLocNode(nodeFactory, mapInfo.getMapLocation()));
        mapNode.put("pass", mapInfo.isPassable());
        mapNode.set("fly", makeRobotInfoNode(nodeFactory, mapInfo.flyingRobot()));
        mapNode.put("wall", mapInfo.isWall());
        mapNode.put("dirt", mapInfo.isDirt());
        mapNode.set("trap", makeTrapTypeNode(nodeFactory, mapInfo.getTrap()));
        mapNode.put("cm", mapInfo.hasCheeseMine());
        mapNode.put("ch", mapInfo.getCheeseAmount());
        return mapNode;
    }

    public static MapInfo parseMapInfoNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        MapLocation loc = parseLocNode(node.get("loc"));
        boolean passable = node.get("pass").asBoolean();
        RobotInfo flyingRobot = parseRobotInfoNode(node.get("fly"));
        boolean wall = node.get("wall").asBoolean();
        boolean dirt = node.get("dirt").asBoolean();
        TrapType trap = trapTypes[node.get("trap").asInt()];
        boolean hasCheeseMine = node.get("cm").asBoolean();
        int cheeseAmount = node.get("ch").asInt();
        return new MapInfo(loc, passable, flyingRobot, wall, dirt, cheeseAmount, trap, hasCheeseMine);
    }

    public static JsonNode makeMessageNode(JsonNodeFactory nodeFactory, Message message) {
        if (message == null) {
            return nodeFactory.nullNode();
        }

        ObjectNode messageNode = nodeFactory.objectNode();
        messageNode.put("type", MESSAGE.ordinal());
        messageNode.put("sid", message.getSenderID());
        messageNode.put("round", message.getRound());
        messageNode.set("loc", makeLocNode(nodeFactory, message.getSource()));
        messageNode.put("bytes", message.getBytes());
        return messageNode;
    }

    public static Message parseMessageNode(JsonNode node) {
        if (node.isNull()) {
            return null;
        }

        int senderID = node.get("sid").asInt();
        int round = node.get("round").asInt();
        MapLocation source = parseLocNode(node.get("loc"));
        int bytes = node.get("bytes").asInt();
        return new Message(bytes, senderID, round, source);
    }

    public static <T> JsonNode makeArrayNode(JsonNodeFactory nodeFactory, T[] array, Function2<JsonNodeFactory, T, JsonNode> mapper) {
        if (array == null) {
            return nodeFactory.nullNode();
        }

        ArrayNode arrayNode = nodeFactory.arrayNode();

        for (T element : array) {
            arrayNode.add(mapper.apply(nodeFactory, element));
        }

        return arrayNode;
    }
}
