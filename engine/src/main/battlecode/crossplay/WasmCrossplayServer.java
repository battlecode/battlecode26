package battlecode.crossplay;

import ai.tegmentum.wasmtime4j.component.ComponentVal;
import ai.tegmentum.webassembly4j.api.*;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.server.Config;

import java.io.*;

import ai.tegmentum.webassembly4j.api.Module;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class WasmCrossplayServer {
    private static final String WIT_API_PREFIX = "battlecode:bot-controller/controller@2026.1.0#";

    private final Engine wasmEngine;

    private int fuelLimit;

    private Map<RobotController, WasmRobot> robots;
    // team name -> the team's compiled wasm module
    private Map<String, Module> teams;

    public record WasmRobot(Instance guest, DefaultLinkingContext linking) {}


    public WasmCrossplayServer(int fuelLimit) {
        wasmEngine = WebAssembly.builder().provider("wasmtime").build();
        this.fuelLimit = fuelLimit;


    }

    public WasmCrossplayServer(Config config) {
        this(100_000);
    }

    public void sendDestroyBot(RobotController robotController) {
        if(robots.containsKey(robotController)){
            WasmRobot instance = robots.remove(robotController);
            // TODO: blowing up
        }
    }

    public void cleanUp() {
        // TODO:
    }

    public void initBot(String teamName, RobotController robotController) {
        // if this is the first time we need to also load the module!
        if(!teams.containsKey(teamName)) {
            try (InputStream inputStream = new FileInputStream(Path.of("crossplay", teamName + ".wasm").toString())) {
                Module module = wasmEngine.loadModule(inputStream);
                teams.put(teamName, module);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Instance guest = teams.get(teamName).instantiate();
        // TODO: handle ai.tegmentum.webassembly4j.api.exception.ExecutionException
        guest.function("init").ifPresentOrElse(
                a -> a.invoke(),
                () -> System.out.println("Team " + teamName + "missing init method."));
        robots.put(robotController, new WasmRobot(guest, createLinkingContext(robotController)));
    }

    public void playTurn(RobotController controller, OutputStream systemOut, String teamName) throws RethrownGameActionException {
        WasmRobot bot = robots.get(controller);
        // TODO: handle ai.tegmentum.webassembly4j.api.exception.ExecutionException
        bot.guest.function("play-turn").ifPresentOrElse(
                a -> a.invoke(),
                () -> System.out.println("Team " + teamName + "missing play turn method."));
    }

    public void printException(Team team, int id, int round, Exception e) {
        String message = e.getMessage(); // TODO: investigate per language special handling?
        String teamStr = team.toString();
        String idStr = Integer.toString(id);
        String formattedMessage = String.format("[%s: #%s@%s] ERROR:\n\n%s\n", teamStr, idStr, round, message);
        System.out.println(formattedMessage);
    }

    @FunctionalInterface
    private interface RobotCall {
        ComponentVal call(Object[] args) throws GameActionException;
    }

    @FunctionalInterface
    private interface RobotAction {
        void run(Object[] args) throws GameActionException;
    }

    private DefaultLinkingContext createLinkingContext(RobotController rc) {
        var b = DefaultLinkingContext.builder();

        addRobotWitFunction(b, "get-round-num",
                a -> ComponentVal.s32(rc.getRoundNum()));
        addRobotWitFunction(b, "get-map-width",
                a -> ComponentVal.s32(rc.getMapWidth()));
        addRobotWitFunction(b, "get-map-height",
                a -> ComponentVal.s32(rc.getMapHeight()));
        addRobotWitFunction(b, "is-cooperation",
                a -> ComponentVal.bool(rc.isCooperation()));
        addRobotWitFunction(b, "get-backstabbing-team",
                a -> optionToWasm(rc.getBackstabbingTeam(), WasmCrossplayServer::enumToWasm));
        addRobotWitFunction(b, "get-number-cat-traps",
                a -> ComponentVal.s32(rc.getNumberCatTraps()));
        addRobotWitFunction(b, "get-number-rat-traps",
                a -> ComponentVal.s32(rc.getNumberRatTraps()));

        addRobotWitFunction(b, "get-id",
                a -> ComponentVal.s32(rc.getID()));
        addRobotWitFunction(b, "get-team",
                a -> enumToWasm(rc.getTeam()));
        addRobotWitFunction(b, "get-location",
                a -> MapLocationToWasm(rc.getLocation()));
        addRobotWitFunction(b, "get-all-part-locations",
                a -> listToWasm(rc.getAllPartLocations(), WasmCrossplayServer::MapLocationToWasm));
        addRobotWitFunction(b, "get-direction",
                a -> enumToWasm(rc.getDirection()));
        addRobotWitFunction(b, "get-health",
                a -> ComponentVal.s32(rc.getHealth()));
        addRobotWitFunction(b, "get-raw-cheese",
                a -> ComponentVal.s32(rc.getRawCheese()));
        addRobotWitFunction(b, "get-global-cheese",
                a -> ComponentVal.s32(rc.getGlobalCheese()));
        addRobotWitFunction(b, "get-all-cheese",
                a -> ComponentVal.s32(rc.getAllCheese()));
        addRobotWitFunction(b, "get-dirt",
                a -> ComponentVal.s32(rc.getDirt()));
        addRobotWitFunction(b, "get-type",
                a -> enumToWasm(rc.getType()));
        addRobotWitFunction(b, "get-carrying",
                a -> optionToWasm(rc.getCarrying(), WasmCrossplayServer::robotInfoToWasm));
        addRobotWitFunction(b, "is-being-thrown",
                a -> ComponentVal.bool(rc.isBeingThrown()));
        addRobotWitFunction(b, "is-being-carried",
                a -> ComponentVal.bool(rc.isBeingCarried()));

        addRobotWitFunction(b, "on-the-map",
                a -> ComponentVal.bool(rc.onTheMap(mapLocationFromWasm(a[0]))));
        addRobotWitFunction(b, "can-sense-location",
                a -> ComponentVal.bool(rc.canSenseLocation(mapLocationFromWasm(a[0]))));
        addFallibleWitFunction(b, "is-location-occupied",
                a -> ComponentVal.bool(rc.isLocationOccupied(mapLocationFromWasm(a[0]))));
        addRobotWitFunction(b, "can-sense-robot-at-location",
                a -> ComponentVal.bool(rc.canSenseRobotAtLocation(mapLocationFromWasm(a[0]))));
        addFallibleWitFunction(b, "sense-robot-at-location",
                a -> optionToWasm(rc.senseRobotAtLocation(mapLocationFromWasm(a[0])), WasmCrossplayServer::robotInfoToWasm));
        addRobotWitFunction(b, "can-sense-robot",
                a -> ComponentVal.bool(rc.canSenseRobot(intFromWasm(a[0]))));
        addFallibleWitFunction(b, "sense-robot",
                a -> robotInfoToWasm(rc.senseRobot(intFromWasm(a[0]))));
        addRobotWitFunction(b, "sense-nearby-robots",
                a -> listToWasm(rc.senseNearbyRobots(), WasmCrossplayServer::robotInfoToWasm));
        addFallibleWitFunction(b, "sense-nearby-robots-within",
                a -> listToWasm(rc.senseNearbyRobots(intFromWasm(a[0])), WasmCrossplayServer::robotInfoToWasm));
        addFallibleWitFunction(b, "sense-nearby-robots-within-team",
                a -> listToWasm(rc.senseNearbyRobots(intFromWasm(a[0]), optionalTeamFromWasm(a[1])), WasmCrossplayServer::robotInfoToWasm));
        addFallibleWitFunction(b, "sense-nearby-robots-at",
                a -> listToWasm(rc.senseNearbyRobots(mapLocationFromWasm(a[0]), intFromWasm(a[1]), optionalTeamFromWasm(a[2])), WasmCrossplayServer::robotInfoToWasm));
        addFallibleWitFunction(b, "sense-passability",
                a -> ComponentVal.bool(rc.sensePassability(mapLocationFromWasm(a[0]))));
        addFallibleWitFunction(b, "sense-map-info",
                a -> mapInfoToWasm(rc.senseMapInfo(mapLocationFromWasm(a[0]))));
        addRobotWitFunction(b, "sense-nearby-map-infos",
                a -> listToWasm(rc.senseNearbyMapInfos(), WasmCrossplayServer::mapInfoToWasm));
        addFallibleWitFunction(b, "sense-nearby-map-infos-within",
                a -> listToWasm(rc.senseNearbyMapInfos(intFromWasm(a[0])), WasmCrossplayServer::mapInfoToWasm));
        addFallibleWitFunction(b, "sense-nearby-map-infos-at",
                a -> listToWasm(rc.senseNearbyMapInfos(mapLocationFromWasm(a[0])), WasmCrossplayServer::mapInfoToWasm));
        addFallibleWitFunction(b, "sense-nearby-map-infos-at-within",
                a -> listToWasm(rc.senseNearbyMapInfos(mapLocationFromWasm(a[0]), intFromWasm(a[1])), WasmCrossplayServer::mapInfoToWasm));
        addRobotWitFunction(b, "adjacent-location",
                a -> MapLocationToWasm(rc.adjacentLocation(directionFromWasm(a[0]))));
        addFallibleWitFunction(b, "get-all-locations-within-radius-squared",
                a -> listToWasm(rc.getAllLocationsWithinRadiusSquared(mapLocationFromWasm(a[0]), intFromWasm(a[1])), WasmCrossplayServer::MapLocationToWasm));

        addRobotWitFunction(b, "is-action-ready",
                a -> ComponentVal.bool(rc.isActionReady()));
        addRobotWitFunction(b, "get-action-cooldown-turns",
                a -> ComponentVal.s32(rc.getActionCooldownTurns()));
        addRobotWitFunction(b, "is-movement-ready",
                a -> ComponentVal.bool(rc.isMovementReady()));
        addRobotWitFunction(b, "is-turning-ready",
                a -> ComponentVal.bool(rc.isTurningReady()));
        addRobotWitFunction(b, "get-movement-cooldown-turns",
                a -> ComponentVal.s32(rc.getMovementCooldownTurns()));
        addRobotWitFunction(b, "get-turning-cooldown-turns",
                a -> ComponentVal.s32(rc.getTurningCooldownTurns()));
        addRobotWitFunction(b, "can-move-forward",
                a -> ComponentVal.bool(rc.canMoveForward()));
        addRobotWitFunction(b, "can-move",
                a -> ComponentVal.bool(rc.canMove(directionFromWasm(a[0]))));
        addFallibleWitAction(b, "move-forward",
                a -> rc.moveForward());
        addFallibleWitAction(b, "move",
                a -> rc.move(directionFromWasm(a[0])));
        addRobotWitFunction(b, "can-turn",
                a -> ComponentVal.bool(rc.canTurn()));
        addRobotWitFunction(b, "can-turn-to",
                a -> ComponentVal.bool(rc.canTurn(directionFromWasm(a[0]))));
        addFallibleWitAction(b, "turn",
                a -> rc.turn(directionFromWasm(a[0])));

        addRobotWitFunction(b, "get-current-rat-cost",
                a -> ComponentVal.s32(rc.getCurrentRatCost()));
        addRobotWitFunction(b, "can-build-rat",
                a -> ComponentVal.bool(rc.canBuildRat(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "build-rat",
                a -> rc.buildRat(mapLocationFromWasm(a[0])));
        addRobotWitFunction(b, "can-become-rat-king",
                a -> ComponentVal.bool(rc.canBecomeRatKing()));
        addFallibleWitAction(b, "become-rat-king",
                a -> rc.becomeRatKing());

        addRobotWitFunction(b, "can-place-dirt",
                a -> ComponentVal.bool(rc.canPlaceDirt(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "place-dirt",
                a -> rc.placeDirt(mapLocationFromWasm(a[0])));
        addRobotWitFunction(b, "can-remove-dirt",
                a -> ComponentVal.bool(rc.canRemoveDirt(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "remove-dirt",
                a -> rc.removeDirt(mapLocationFromWasm(a[0])));
        addRobotWitFunction(b, "can-place-rat-trap",
                a -> ComponentVal.bool(rc.canPlaceRatTrap(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "place-rat-trap",
                a -> rc.placeRatTrap(mapLocationFromWasm(a[0])));
        addRobotWitFunction(b, "can-remove-rat-trap",
                a -> ComponentVal.bool(rc.canRemoveRatTrap(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "remove-rat-trap",
                a -> rc.removeRatTrap(mapLocationFromWasm(a[0])));
        addRobotWitFunction(b, "can-place-cat-trap",
                a -> ComponentVal.bool(rc.canPlaceCatTrap(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "place-cat-trap",
                a -> rc.placeCatTrap(mapLocationFromWasm(a[0])));
        addRobotWitFunction(b, "can-remove-cat-trap",
                a -> ComponentVal.bool(rc.canRemoveCatTrap(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "remove-cat-trap",
                a -> rc.removeCatTrap(mapLocationFromWasm(a[0])));

        addRobotWitFunction(b, "can-pick-up-cheese",
                a -> ComponentVal.bool(rc.canPickUpCheese(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "pick-up-cheese",
                a -> rc.pickUpCheese(mapLocationFromWasm(a[0])));
        addFallibleWitAction(b, "pick-up-cheese-amount",
                a -> rc.pickUpCheese(mapLocationFromWasm(a[0]), intFromWasm(a[1])));
        addRobotWitFunction(b, "can-transfer-cheese",
                a -> ComponentVal.bool(rc.canTransferCheese(mapLocationFromWasm(a[0]), intFromWasm(a[1]))));
        addFallibleWitAction(b, "transfer-cheese",
                a -> rc.transferCheese(mapLocationFromWasm(a[0]), intFromWasm(a[1])));

        addRobotWitFunction(b, "can-attack",
                a -> ComponentVal.bool(rc.canAttack(mapLocationFromWasm(a[0]))));
        addRobotWitFunction(b, "can-attack-with-cheese",
                a -> ComponentVal.bool(rc.canAttack(mapLocationFromWasm(a[0]), intFromWasm(a[1]))));
        addFallibleWitAction(b, "attack",
                a -> rc.attack(mapLocationFromWasm(a[0])));
        addFallibleWitAction(b, "attack-with-cheese",
                a -> rc.attack(mapLocationFromWasm(a[0]), intFromWasm(a[1])));

        addRobotWitFunction(b, "squeak",
                a -> ComponentVal.bool(rc.squeak(intFromWasm(a[0]))));
        addRobotWitFunction(b, "read-squeaks",
                a -> listToWasm(rc.readSqueaks(intFromWasm(a[0])), WasmCrossplayServer::messageToWasm));
        addFallibleWitAction(b, "write-shared-array",
                a -> rc.writeSharedArray(intFromWasm(a[0]), intFromWasm(a[1])));
        addFallibleWitFunction(b, "read-shared-array",
                a -> ComponentVal.s32(rc.readSharedArray(intFromWasm(a[0]))));

        addRobotWitFunction(b, "can-throw-rat",
                a -> ComponentVal.bool(rc.canThrowRat()));
        addFallibleWitAction(b, "throw-rat",
                a -> rc.throwRat());
        addRobotWitFunction(b, "can-drop-rat",
                a -> ComponentVal.bool(rc.canDropRat(directionFromWasm(a[0]))));
        addFallibleWitAction(b, "drop-rat",
                a -> rc.dropRat(directionFromWasm(a[0])));
        addRobotWitFunction(b, "can-carry-rat",
                a -> ComponentVal.bool(rc.canCarryRat(mapLocationFromWasm(a[0]))));
        addFallibleWitAction(b, "carry-rat",
                a -> rc.carryRat(mapLocationFromWasm(a[0])));

        addRobotWitAction(b, "disintegrate",
                a -> rc.disintegrate());
        addRobotWitAction(b, "resign",
                a -> rc.resign());

        addRobotWitAction(b, "set-indicator-string",
                a -> rc.setIndicatorString(stringFromWasm(a[0])));
        addFallibleWitAction(b, "set-indicator-dot",
                a -> rc.setIndicatorDot(mapLocationFromWasm(a[0]), intFromWasm(a[1]), intFromWasm(a[2]), intFromWasm(a[3])));
        addFallibleWitAction(b, "set-indicator-line",
                a -> rc.setIndicatorLine(mapLocationFromWasm(a[0]), mapLocationFromWasm(a[1]), intFromWasm(a[2]), intFromWasm(a[3]), intFromWasm(a[4])));
        addRobotWitAction(b, "set-timeline-marker",
                a -> rc.setTimelineMarker(stringFromWasm(a[0]), intFromWasm(a[1]), intFromWasm(a[2]), intFromWasm(a[3])));

        return b.build();
    }

    private static void addRobotWitFunction(DefaultLinkingContext.Builder builder, String methodName, Function<Object[], ComponentVal> func) {
        builder.addWitHostFunction(WIT_API_PREFIX + methodName, args -> new Object[] { func.apply(args) });
    }

    private static void addRobotWitAction(DefaultLinkingContext.Builder builder, String methodName, Consumer<Object[]> action) {
        builder.addWitHostFunction(WIT_API_PREFIX + methodName, args -> {
            action.accept(args);
            return new Object[0];
        });
    }

    private static void addFallibleWitFunction(DefaultLinkingContext.Builder builder, String methodName, RobotCall func) {
        addRobotWitFunction(builder, methodName, args -> {
            try {
                return ComponentVal.ok(func.call(args));
            } catch (GameActionException e) {
                return ComponentVal.err(gameActionErrorToWasm(e));
            }
        });
    }

    private static void addFallibleWitAction(DefaultLinkingContext.Builder builder, String methodName, RobotAction action) {
        addRobotWitFunction(builder, methodName, args -> {
            try {
                action.run(args);
                return ComponentVal.ok();
            } catch (GameActionException e) {
                return ComponentVal.err(gameActionErrorToWasm(e));
            }
        });
    }

    private static ComponentVal MapLocationToWasm(MapLocation loc)
    {
        return ComponentVal.record(Map.of(
                "x", ComponentVal.s32(loc.x),
                "y", ComponentVal.s32(loc.y)
        ));
    }

    private static ComponentVal enumToWasm(Enum<?> e) {
        return ComponentVal.enum_(e.name().toLowerCase().replace('_', '-'));
    }

    private static <T> ComponentVal optionToWasm(T value, Function<T, ComponentVal> toWasm) {
        return value == null ? ComponentVal.none() : ComponentVal.some(toWasm.apply(value));
    }

    private static <T> ComponentVal listToWasm(T[] items, Function<T, ComponentVal> toWasm) {
        return ComponentVal.list(Arrays.stream(items).map(toWasm).toArray(ComponentVal[]::new));
    }

    private static Map<String, ComponentVal> robotFields(RobotInfo r) {
        return Map.of(
                "id", ComponentVal.s32(r.ID),
                "team", enumToWasm(r.team),
                "unit-type", enumToWasm(r.type),
                "health", ComponentVal.s32(r.health),
                "location", MapLocationToWasm(r.location),
                "direction", enumToWasm(r.direction),
                "chirality", ComponentVal.s32(r.chirality),
                "cheese-amount", ComponentVal.s32(r.cheeseAmount)
        );
    }

    private static ComponentVal carriedRobotInfoToWasm(RobotInfo r) {
        return ComponentVal.record(robotFields(r));
    }

    private static ComponentVal robotInfoToWasm(RobotInfo r) {
        var fields = new LinkedHashMap<>(robotFields(r));
        fields.put("carrying", optionToWasm(r.carryingRobot, WasmCrossplayServer::carriedRobotInfoToWasm));
        return ComponentVal.record(fields);
    }

    private static ComponentVal mapInfoToWasm(MapInfo m) {
        return ComponentVal.record(Map.of(
                "location", MapLocationToWasm(m.getMapLocation()),
                "is-passable", ComponentVal.bool(m.isPassable()),
                "flying-robot", optionToWasm(m.flyingRobot(), WasmCrossplayServer::robotInfoToWasm),
                "is-wall", ComponentVal.bool(m.isWall()),
                "is-dirt", ComponentVal.bool(m.isDirt()),
                "cheese-amount", ComponentVal.s32(m.getCheeseAmount()),
                "trap", enumToWasm(m.getTrap()),
                "has-cheese-mine", ComponentVal.bool(m.hasCheeseMine())
        ));
    }

    private static ComponentVal messageToWasm(Message m) {
        return ComponentVal.record(Map.of(
                "bytes", ComponentVal.s32(m.getBytes()),
                "sender-id", ComponentVal.s32(m.getSenderID()),
                "round", ComponentVal.s32(m.getRound()),
                "source", MapLocationToWasm(m.getSource())
        ));
    }

    private static ComponentVal gameActionErrorToWasm(GameActionException e) {
        return ComponentVal.record(Map.of(
                "kind", enumToWasm(e.getType()),
                "message", ComponentVal.string(String.valueOf(e.getMessage()))
        ));
    }

    private static int intFromWasm(Object v) {
        return ((ComponentVal) v).asS32();
    }

    private static String stringFromWasm(Object v) {
        return ((ComponentVal) v).asString();
    }

    private static MapLocation mapLocationFromWasm(Object v) {
        Map<String, ComponentVal> fields = ((ComponentVal) v).asRecord();
        return new MapLocation(fields.get("x").asS32(), fields.get("y").asS32());
    }

    private static Direction directionFromWasm(Object v) {
        return Direction.valueOf(((ComponentVal) v).asEnum().toUpperCase());
    }

    private static Team optionalTeamFromWasm(Object v) {
        return ((ComponentVal) v).asSome().map(t -> Team.valueOf(t.asEnum().toUpperCase())).orElse(null);
    }
}
