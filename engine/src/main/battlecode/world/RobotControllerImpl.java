package battlecode.world;

import battlecode.common.*;

import static battlecode.common.GameActionExceptionType.*;
import battlecode.schema.Action;
import battlecode.util.FlatHelpers;
import battlecode.instrumenter.RobotDeathException;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;

/**
 * The actual implementation of RobotController. Its methods *must* be called
 * from a player thread.
 *
 * It is theoretically possible to have multiple for a single InternalRobot, but
 * that may cause problems in practice, and anyway why would you want to?
 *
 * All overriden methods should assertNotNull() all of their (Object) arguments,
 * if those objects are not explicitly stated to be nullable.
 */
public final class RobotControllerImpl implements RobotController {

    /**
     * The world the robot controlled by this controller inhabits.
     */
    private final GameWorld gameWorld;

    /**
     * The robot this controller controls.
     */
    private final InternalRobot robot;

    /**
     * Create a new RobotControllerImpl
     * 
     * @param gameWorld the relevant world
     * @param robot     the relevant robot
     */
    public RobotControllerImpl(GameWorld gameWorld, InternalRobot robot) {
        this.gameWorld = gameWorld;
        this.robot = robot;
    }

    // *********************************
    // ******** INTERNAL METHODS *******
    // *********************************

    /**
     * Throw a null pointer exception if an object is null.
     *
     * @param o the object to test
     */
    private static void assertNotNull(Object o) {
        if (o == null) {
            throw new NullPointerException("Argument has an invalid null value");
        }
    }

    @Override
    public int hashCode() {
        return getID();
    }

    private InternalRobot getRobotByID(int id) {
        if (!this.gameWorld.getObjectInfo().existsRobot(id))
            return null;
        return this.gameWorld.getObjectInfo().getRobotByID(id);
    }

    private int locationToInt(MapLocation loc) {
        return this.gameWorld.locationToIndex(loc);
    }

    private MapInfo getMapInfo(MapLocation loc) throws GameActionException {
        GameWorld gw = this.gameWorld;
        Trap trap = gw.getTrap(loc);
        TrapType trapType = (trap != null && trap.getTeam() == this.getTeam()) ? trap.getType() : TrapType.NONE;
        MapInfo currentLocInfo = new MapInfo(loc, gw.isPassable(loc), gw.getWall(loc), gw.getDirt(loc), trapType,
                gw.hasCheeseMine(loc));
        return currentLocInfo;
    }

    // *********************************
    // ****** GLOBAL QUERY METHODS *****
    // *********************************

    @Override
    public int getRoundNum() {
        return this.gameWorld.getCurrentRound();
    }

    @Override
    public int getMapWidth() {
        return this.gameWorld.getGameMap().getWidth();
    }

    @Override
    public int getMapHeight() {
        return this.gameWorld.getGameMap().getHeight();
    }

    // *********************************
    // ****** UNIT QUERY METHODS *******
    // *********************************

    @Override
    public int getID() {
        return this.robot.getID();
    }

    @Override
    public Team getTeam() {
        return this.robot.getTeam();
    }

    @Override
    public int getRawCheese() {
        return this.robot.getCheese();
    }

    @Override
    public int getGlobalCheese() {
        return this.gameWorld.getTeamInfo().getCheese(getTeam());
    }

    @Override
    public int getAllCheese() {
        return getRawCheese() + getGlobalCheese();
    }

    @Override
    public MapLocation getLocation() {
        return this.robot.getLocation();
    }

    @Override
    public Direction getDirection() {
        return this.robot.getDirection();
    }

    @Override
    public int getHealth() {
        return this.robot.getHealth();
    }

    @Override
    public int getDirt() {
        return this.gameWorld.getTeamInfo().getDirt(getTeam());
    }

    @Override
    public UnitType getType() {
        return this.robot.getType();
    }

    // ***********************************
    // ****** GENERAL VISION METHODS *****
    // ***********************************

    @Override
    public boolean onTheMap(MapLocation loc) {
        assertNotNull(loc);
        if (!this.gameWorld.getGameMap().onTheMap(loc))
            return false;
        return true;
    }

    private void assertCanSenseLocation(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        if (!this.gameWorld.getGameMap().onTheMap(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location is not on the map");
        if (!this.robot.canSenseLocation(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location not within vision range");
    }

    private void assertCanActLocation(MapLocation loc, int maxRadiusSquared) throws GameActionException {
        // assumes maxRadiusSquared <= visionRadiusSquared.
        // This handles the angle checking, so we only check distance.
        assertCanSenseLocation(loc);
        int distance = (this.getType().usesTopRightLocationForDistance())
                ? (getLocation().topLeftDistanceSquaredTo(loc))
                : (getLocation().distanceSquaredTo(loc));
        if (distance > maxRadiusSquared)
            throw new GameActionException(OUT_OF_RANGE,
                    "Target location not within action range");
    }

    private void assertCanActOffCenterLocation(MapLocation loc, int maxRadiusSquared) throws GameActionException {
        assertNotNull(loc);
        if (getLocation().topLeftDistanceSquaredTo(loc) > maxRadiusSquared)
            throw new GameActionException(OUT_OF_RANGE,
                    "Target location not within action range");
        if (!this.gameWorld.getGameMap().onTheMap(loc))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Target location is not on the map");
    }

    private void assertCanPlaceDirt(MapLocation loc) throws GameActionException {
        assertIsRobotType(this.robot.getType());
        // Use unit action radius as the allowed range for the action
        assertCanActLocation(loc, GameConstants.BUILD_DISTANCE_SQUARED);

        // state checks :
        if (this.gameWorld.getTeamInfo().getDirt(this.robot.getTeam()) <= 0)
            throw new GameActionException(CANT_DO_THAT, "No dirt available to place!");
        if (this.getAllCheese() < GameConstants.PLACE_DIRT_CHEESE_COST)
            throw new GameActionException(CANT_DO_THAT, "Insufficient cheese to place dirt!");
        if (this.gameWorld.getWall(loc))
            throw new GameActionException(CANT_DO_THAT, "Can't place dirt on a wall!");
        if (this.gameWorld.getRobot(loc) != null)
            throw new GameActionException(CANT_DO_THAT, "Can't place dirt on an occupied tile!");
        if (this.gameWorld.getDirt(loc))
            throw new GameActionException(CANT_DO_THAT, "Tile already has dirt!");

        this.robot.addActionCooldownTurns(GameConstants.DIG_COOLDOWN);
    }

    private void assertCanRemoveDirt(MapLocation loc) throws GameActionException {
        assertIsRobotType(this.robot.getType());
        assertCanActLocation(loc, GameConstants.BUILD_DISTANCE_SQUARED);

        if (this.robot.getType().isRatType() && this.getAllCheese() < GameConstants.DIG_DIRT_CHEESE_COST)
            throw new GameActionException(CANT_DO_THAT, "Insufficient cheese to remove dirt!");
        if (!this.gameWorld.getDirt(loc))
            throw new GameActionException(CANT_DO_THAT, "No dirt to remove at that location!");

        this.robot.addActionCooldownTurns(GameConstants.DIG_COOLDOWN);
    }

    @Override
    public boolean canPlaceDirt(MapLocation loc) {
        try {
            assertCanPlaceDirt(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void placeDirt(MapLocation loc) {
        if (canPlaceDirt(loc)) {
            this.gameWorld.setDirt(loc, true);
            this.gameWorld.getTeamInfo().updateDirt(this.robot.getTeam(), true);
            this.robot.addCheese(-1 * GameConstants.PLACE_DIRT_CHEESE_COST);
        }
    }

    @Override
    public boolean canRemoveDirt(MapLocation loc) {
        try {
            assertCanRemoveDirt(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    private void assertCanPlaceRatTrap(MapLocation loc) throws GameActionException {
        assertIsRobotType(this.robot.getType());
        assertCanActLocation(loc, GameConstants.BUILD_DISTANCE_SQUARED);

        if (this.gameWorld.getWall(loc))
            throw new GameActionException(CANT_DO_THAT, "Can't place rat trap on a wall!");
        if (this.gameWorld.getRobot(loc) != null)
            throw new GameActionException(CANT_DO_THAT, "Can't place rat trap on an occupied tile!");
        if (this.gameWorld.hasRatTrap(loc))
            throw new GameActionException(CANT_DO_THAT, "Tile already has a rat trap!");
        if (this.gameWorld.getTrapCount(TrapType.RAT_TRAP) >= TrapType.RAT_TRAP.maxCount)
            throw new GameActionException(CANT_DO_THAT, "Team has reached maximum number of rat traps on the map!");
    }

    private void assertCanRemoveRatTrap(MapLocation loc) throws GameActionException {
        assertIsRobotType(this.robot.getType());
        assertCanActLocation(loc, GameConstants.BUILD_DISTANCE_SQUARED);

        if (!this.gameWorld.hasRatTrap(loc))
            throw new GameActionException(CANT_DO_THAT, "No rat trap to remove at that location!");
        if (this.gameWorld.getTrap(loc).getTeam() != this.getTeam())
            throw new GameActionException(CANT_DO_THAT, "Can't remove an enemy team's rat trap!");
    }

    private void assertCanPlaceCatTrap(MapLocation loc) throws GameActionException {
        assertIsRobotType(this.robot.getType());
        assertCanActLocation(loc, GameConstants.BUILD_DISTANCE_SQUARED);

        if (this.gameWorld.getWall(loc))
            throw new GameActionException(CANT_DO_THAT, "Can't place cat trap on a wall!");
        if (this.gameWorld.getRobot(loc) != null)
            throw new GameActionException(CANT_DO_THAT, "Can't place cat trap on an occupied tile!");
        if (this.gameWorld.hasCatTrap(loc))
            throw new GameActionException(CANT_DO_THAT, "Tile already has a cat trap!");
        if (this.gameWorld.getTrapCount(TrapType.CAT_TRAP) >= TrapType.CAT_TRAP.maxCount)
            throw new GameActionException(CANT_DO_THAT, "Team has reached maximum number of cat traps on the map!");
    }

    private void assertCanRemoveCatTrap(MapLocation loc) throws GameActionException {
        assertIsRobotType(this.robot.getType());
        assertCanActLocation(loc, GameConstants.BUILD_DISTANCE_SQUARED);

        if (!this.gameWorld.hasCatTrap(loc))
            throw new GameActionException(CANT_DO_THAT, "No cat trap to remove at that location!");
    }

    @Override
    public boolean canPlaceRatTrap(MapLocation loc) {
        try {
            assertCanPlaceRatTrap(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void placeRatTrap(MapLocation loc) throws GameActionException {
        assertCanPlaceRatTrap(loc);
        this.gameWorld.placeTrap(loc, TrapType.RAT_TRAP, getTeam());
    }

    @Override
    public boolean canRemoveRatTrap(MapLocation loc) {
        try {
            assertCanRemoveRatTrap(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void removeRatTrap(MapLocation loc) throws GameActionException {
        assertCanRemoveRatTrap(loc);
        this.gameWorld.removeTrap(loc);
    }

    @Override
    public boolean canPlaceCatTrap(MapLocation loc) {
        try {
            assertCanPlaceCatTrap(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void placeCatTrap(MapLocation loc) throws GameActionException {
        assertCanPlaceCatTrap(loc);
        this.gameWorld.placeTrap(loc, TrapType.CAT_TRAP, getTeam());
    }

    @Override
    public boolean canRemoveCatTrap(MapLocation loc) {
        try {
            assertCanRemoveCatTrap(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void removeCatTrap(MapLocation loc) throws GameActionException {
        assertCanRemoveCatTrap(loc);
        this.gameWorld.removeTrap(loc);
    }

    @Override
    public void removeDirt(MapLocation loc) {
        if (canRemoveDirt(loc)) {
            this.gameWorld.setDirt(loc, false);
            this.gameWorld.getTeamInfo().updateDirt(this.robot.getTeam(), false);
            if (this.robot.getType().isRatType())
                this.robot.addCheese(-1 * GameConstants.DIG_DIRT_CHEESE_COST);
        }
    }

    @Override
    public boolean canSenseLocation(MapLocation loc) {
        try {
            assertCanSenseLocation(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public boolean isLocationOccupied(MapLocation loc) throws GameActionException {
        assertCanSenseLocation(loc);
        return this.gameWorld.getRobot(loc) != null;
    }

    @Override
    public boolean canSenseRobotAtLocation(MapLocation loc) {
        try {
            return isLocationOccupied(loc);
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public RobotInfo senseRobotAtLocation(MapLocation loc) throws GameActionException {
        assertCanSenseLocation(loc);
        InternalRobot bot = this.gameWorld.getRobot(loc);
        return bot == null ? null : bot.getRobotInfo();
    }

    @Override
    public boolean canSenseRobot(int id) {
        InternalRobot sensedRobot = getRobotByID(id);
        return sensedRobot != null && canSenseLocation(sensedRobot.getLocation());
    }

    @Override
    public RobotInfo senseRobot(int id) throws GameActionException {
        if (!canSenseRobot(id))
            throw new GameActionException(CANT_SENSE_THAT,
                    "Can't sense given robot; It may be out of vision range or not exist anymore");
        return getRobotByID(id).getRobotInfo();
    }

    private void assertRadiusNonNegative(int radiusSquared) throws GameActionException {
        if (radiusSquared < -1) {
            throw new GameActionException(CANT_DO_THAT, "The radius for a sense command can't be negative and not -1");
        }
    }

    @Override
    public RobotInfo[] senseNearbyRobots() {
        try {
            return senseNearbyRobots(-1);
        } catch (GameActionException e) {
            return new RobotInfo[0];
        }
    }

    @Override
    public RobotInfo[] senseNearbyRobots(int radiusSquared) throws GameActionException {
        assertRadiusNonNegative(radiusSquared);
        return senseNearbyRobots(radiusSquared, null);
    }

    @Override
    public RobotInfo[] senseNearbyRobots(int radiusSquared, Team team) throws GameActionException {
        assertRadiusNonNegative(radiusSquared);
        return senseNearbyRobots(getLocation(), radiusSquared, team);
    }

    @Override
    public RobotInfo[] senseNearbyRobots(MapLocation center, int radiusSquared, Team team) throws GameActionException {
        assertNotNull(center);
        assertRadiusNonNegative(radiusSquared);
        int actualRadiusSquared = radiusSquared == -1 ? this.robot.getVisionRadiusSquared()
                : Math.min(radiusSquared, this.robot.getVisionRadiusSquared());
        InternalRobot[] allSensedRobots = gameWorld.getAllRobotsWithinRadiusSquared(center, actualRadiusSquared, team);
        List<RobotInfo> validSensedRobots = new ArrayList<>();
        for (InternalRobot sensedRobot : allSensedRobots) {
            // check if this robot
            if (sensedRobot.equals(this.robot))
                continue;
            // check if can sense
            if (!canSenseLocation(sensedRobot.getLocation()))
                continue;
            // check if right team
            if (team != null && sensedRobot.getTeam() != team)
                continue;
            validSensedRobots.add(sensedRobot.getRobotInfo());
        }
        return validSensedRobots.toArray(new RobotInfo[validSensedRobots.size()]);
    }

    @Override
    public boolean sensePassability(MapLocation loc) throws GameActionException {
        assertCanSenseLocation(loc);
        return this.gameWorld.isPassable(loc);
    }

    @Override
    public MapInfo senseMapInfo(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        assertCanSenseLocation(loc);
        return getMapInfo(loc);
    }

    @Override
    public MapInfo[] senseNearbyMapInfos() {
        try {
            return senseNearbyMapInfos(-1);
        } catch (GameActionException e) {
            return new MapInfo[0];
        }
    }

    @Override
    public MapInfo[] senseNearbyMapInfos(int radiusSquared) throws GameActionException {
        assertRadiusNonNegative(radiusSquared);
        return senseNearbyMapInfos(getLocation(), radiusSquared);
    }

    @Override
    public MapInfo[] senseNearbyMapInfos(MapLocation center) throws GameActionException {
        assertNotNull(center);
        return senseNearbyMapInfos(center, -1);
    }

    @Override
    public MapInfo[] senseNearbyMapInfos(MapLocation center, int radiusSquared) throws GameActionException {
        assertNotNull(center);
        assertRadiusNonNegative(radiusSquared);
        int actualRadiusSquared = radiusSquared == -1 ? UnitType.RAT.visionConeRadiusSquared
                : Math.min(radiusSquared, UnitType.RAT.visionConeRadiusSquared);
        MapLocation[] allSensedLocs = gameWorld.getAllLocationsWithinRadiusSquared(center, actualRadiusSquared);
        List<MapInfo> validSensedMapInfo = new ArrayList<>();
        for (MapLocation mapLoc : allSensedLocs) {
            // Can't actually sense location
            if (!canSenseLocation(mapLoc)) {
                continue;
            }
            MapInfo mapInfo = getMapInfo(mapLoc);
            validSensedMapInfo.add(mapInfo);
        }
        return validSensedMapInfo.toArray(new MapInfo[validSensedMapInfo.size()]);
    }

    @Override
    public MapLocation adjacentLocation(Direction dir) {
        return getLocation().add(dir);
    }

    @Override
    public MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared)
            throws GameActionException {
        assertNotNull(center);
        assertRadiusNonNegative(radiusSquared);
        int actualRadiusSquared = radiusSquared == -1 ? this.robot.getVisionRadiusSquared()
                : Math.min(radiusSquared, this.robot.getVisionRadiusSquared());
        MapLocation[] possibleLocs = this.gameWorld.getAllLocationsWithinConeRadiusSquared(center,
                this.robot.getDirection(), this.robot.getVisionConeAngle(), actualRadiusSquared);
        List<MapLocation> visibleLocs = Arrays.asList(possibleLocs).stream().filter(x -> canSenseLocation(x))
                .collect(Collectors.toList());
        return visibleLocs.toArray(new MapLocation[visibleLocs.size()]);
    }

    // ***********************************
    // ****** READINESS METHODS **********
    // ***********************************

    private void assertIsActionReady() throws GameActionException {
        if (!this.robot.canActCooldown())
            throw new GameActionException(IS_NOT_READY,
                    "This robot's action cooldown has not expired.");
    }

    @Override
    public boolean isActionReady() {
        try {
            assertIsActionReady();
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public int getActionCooldownTurns() {
        return this.robot.getActionCooldownTurns();
    }

    private void assertIsMovementReady() throws GameActionException {
        if (!this.robot.canMoveCooldown())
            throw new GameActionException(IS_NOT_READY,
                    "This robot's movement cooldown has not expired.");
    }

    @Override
    public boolean isMovementReady() {
        try {
            assertIsMovementReady();
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public int getMovementCooldownTurns() {
        return this.robot.getMovementCooldownTurns();
    }

    // ***********************************
    // ****** MOVEMENT METHODS ***********
    // ***********************************

    private void assertCanMoveForward() throws GameActionException {
        assertIsMovementReady();
        MapLocation[] curLocs = robot.getAllPartLocations();

        MapLocation[] newLocs = new MapLocation[curLocs.length];
        for (int i = 0; i < newLocs.length; i++) {
            newLocs[i] = curLocs[i].add(robot.getDirection());
        }

        for (MapLocation loc : newLocs) {
            if (!onTheMap(loc))
                throw new GameActionException(OUT_OF_RANGE,
                        "Can only move to locations on the map; " + loc + " is not on the map. Currently at location "
                                + this.getLocation());
            if ((this.gameWorld.getRobot(loc) != null) && (this.gameWorld.getRobot(loc).getID() != robot.getID())) {
                System.out.println("DEBUGGING: " + " collision with robot of type "
                        + this.gameWorld.getRobot(loc).getType() + " with part locations at ");
                MapLocation[] partLocs = robot.getAllPartLocations();
                System.out.print("Part locations: [");
                for (int i = 0; i < partLocs.length; i++) {
                    System.out.print("(" + partLocs[i].x + ", " + partLocs[i].y + ")");
                    if (i < partLocs.length - 1)
                        System.out.print(", ");
                }
                System.out.println("]");
                throw new GameActionException(CANT_MOVE_THERE,
                        "Cannot move to an occupied location; " + loc + " is occupied by a different robot.");
            }
            if (!this.gameWorld.isPassable(loc))
                throw new GameActionException(CANT_MOVE_THERE,
                        "Cannot move to an impassable location; " + loc + " is impassable.");
        }
    }

    @Override
    public boolean canMoveForward() {
        try {
            assertCanMoveForward();
            return true;
        } catch (GameActionException e) {
            if (this.robot.getID() == 2)
                e.printStackTrace();
            return false;
        }
    }

    @Override
    public void moveForward() throws GameActionException {
        assertCanMoveForward();

        // calculate set of next map locations
        MapLocation[] curLocs = robot.getAllPartLocations();
        MapLocation[] newLocs = new MapLocation[curLocs.length];
        Direction dir = robot.getDirection();
        for (int i = 0; i < newLocs.length; i++) {
            MapLocation curLoc = curLocs[i];
            newLocs[i] = curLoc.add(dir);
            this.gameWorld.removeRobot(curLoc);
        }
        this.robot.setLocation(dir.dx, dir.dy);
        for (int i = 0; i < newLocs.length; i++) {
            MapLocation newLoc = newLocs[i];
            this.gameWorld.addRobot(newLoc, this.robot);

            for (int j = this.gameWorld.getTrapTriggers(newLoc).size() - 1; j >= 0; j--) {
                Trap trap = this.gameWorld.getTrapTriggers(newLoc).get(j);
                if (trap.getTeam() == this.robot.getTeam() || trap.getType() == TrapType.RAT_TRAP) {
                    continue;
                }
                this.robot.addTrapTrigger(trap, true);
            }
        }

        this.robot.addMovementCooldownTurns();
    }

    private void assertCanTurn(int steps) throws GameActionException {
        if (steps < 0) {
            throw new GameActionException(CANT_DO_THAT,
                    "Number of steps to turn must be non-negative");
        }
        if (steps > 2) {
            throw new GameActionException(CANT_DO_THAT,
                    "Can only turn up to 2 steps (90 degrees) at a time");
        }
        if (this.robot.hasTurned()) {
            throw new GameActionException(CANT_DO_THAT,
                    "This robot has already turned this turn");
        }
    }

    @Override
    public boolean canTurnCW(int steps) {
        try {
            assertCanTurn(steps);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void turnCW(int steps) throws GameActionException {
        assertCanTurn(steps);
        Direction newDir = this.robot.getDirection();
        for (int i = 0; i < steps; i++) {
            newDir = newDir.rotateRight();
        }
        this.robot.setDirection(newDir);
        this.robot.setHasTurned(true);
    }

    @Override
    public boolean canTurnCCW(int steps) {
        try {
            assertCanTurn(steps);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void turnCCW(int steps) throws GameActionException {
        assertCanTurn(steps);
        Direction newDir = this.robot.getDirection();
        for (int i = 0; i < steps; i++) {
            newDir = newDir.rotateLeft();
        }
        this.robot.setDirection(newDir);
        this.robot.setHasTurned(true);
    }

    // ***********************************
    // ******** BUILDING METHODS *********
    // ***********************************

    @Override
    public int getCurrentRatCost() {
        return GameConstants.BUILD_ROBOT_BASE_COST +
                GameConstants.BUILD_ROBOT_COST_INCREASE * (this.gameWorld.getTeamInfo().getNumRats(getTeam())
                        / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    }

    private void assertIsRobotType(UnitType type) throws GameActionException {
        if (!type.isRobotType()) {
            throw new GameActionException(CANT_DO_THAT, "Given type " + type + " is not a robot type!");
        }
    }

    private void assertCanBuildRobot(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        assertCanActLocation(loc, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        assertIsActionReady();
        if (!this.robot.getType().isRatKingType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rat kings can spawn other robots!");
        }
        int cost = getCurrentRatCost();

        if (this.gameWorld.getTeamInfo().getCheese(this.robot.getTeam()) < cost) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to build new robot!");
        }

        if (isLocationOccupied(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Location is already occupied!");
        }

        if (!sensePassability(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Location has a wall or ruin!");
        }
    }

    @Override
    public boolean canBuildRobot(MapLocation loc) {
        try {
            assertCanBuildRobot(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void buildRobot(MapLocation loc) throws GameActionException {
        assertCanBuildRobot(loc);
        this.robot.addActionCooldownTurns(GameConstants.BUILD_ROBOT_COOLDOWN);
        this.gameWorld.spawnRobot(UnitType.RAT, loc, this.robot.getTeam());
        int cost = getCurrentRatCost();
        this.robot.addCheese(-cost);
        InternalRobot robotSpawned = this.gameWorld.getRobot(loc);
        this.gameWorld.getMatchMaker().addSpawnAction(robotSpawned.getID(), loc, getTeam(), UnitType.RAT);
    }

    public void assertCanBuildTrap(TrapType type, MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        assertNotNull(type);
        assertCanActLocation(loc, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        assertIsActionReady();

        if (getAllCheese() < type.buildCost) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese!");
        }
        if (isLocationOccupied(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Location is already occupied!");
        }
        if (!sensePassability(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Location has a wall or ruin!");
        }
    }

    public boolean canBuildTrap(TrapType type, MapLocation loc) {
        try {
            assertCanBuildTrap(type, loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    public void buildTrap(TrapType type, MapLocation loc) throws GameActionException {
        assertCanBuildTrap(type, loc);
        this.robot.addActionCooldownTurns(type.actionCooldown);
        this.robot.addCheese(-type.buildCost);
        this.gameWorld.placeTrap(loc, type, getTeam());
        int ID = this.gameWorld.idGenerator.nextID();
        this.gameWorld.getMatchMaker().addPlaceTrapAction(ID, loc, getTeam(), type);
    }

    // *****************************
    // ****** ATTACK / HEAL ********
    // *****************************

    private void assertCanAttackRat(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        // TODO: for this and assertCanAttackCat, I don't think we have
        // 'actionRadiusSquared'/attack radii defined anywhere
        assertCanActLocation(loc, -1);
        if (!this.gameWorld.isPassable(loc))
            throw new GameActionException(CANT_DO_THAT, "Rats cannot attack squares with walls or dirt on them!");
    }

    private void assertCanAttackCat(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        assertCanActLocation(loc, -1);
        if (!this.gameWorld.isPassable(loc))
            throw new GameActionException(CANT_DO_THAT, "Cats cannot attack squares with walls or dirt on them!");
    }

    private void assertCanAttack(MapLocation loc) throws GameActionException {
        if (loc == null) {
            throw new GameActionException(CANT_DO_THAT, "Robot units must specify a location to attack");
        }

        switch (this.robot.getType()) {
            case RAT:
                assertCanAttackRat(loc);
                break;
            case CAT:
                assertCanAttackCat(loc);
                break;
            default:
                assertCanAttackRat(loc);
                break;
        }
    }

    @Override
    public boolean canAttack(MapLocation loc) {
        try {
            assertCanAttack(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void attack(MapLocation loc) throws GameActionException {
        assertCanAttack(loc);
        if (this.robot.getType().isRobotType())
            this.robot.addActionCooldownTurns(this.robot.getType().actionCooldown);
        this.robot.attack(loc);
    }

    public void assertCanBecomeRatKing() throws GameActionException {
        assertIsActionReady();
        if (this.gameWorld.getTeamInfo().getCheese(this.robot.getTeam()) < GameConstants.RAT_KING_UPGRADE_CHEESE_COST) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to upgrade to a rat king");
        }
        int numAllyRats = 0;
        for (Direction d : Direction.allDirections()) {
            MapLocation curLoc = this.adjacentLocation(d);
            InternalRobot curRobot = this.gameWorld.getRobot(curLoc);
            if (curRobot != null && curRobot.getTeam() == this.robot.getTeam() && curRobot.getType() == UnitType.RAT) {
                numAllyRats += 1;
            }
            if (curRobot != null && !curRobot.getType().isRatType()) {
                throw new GameActionException(CANT_DO_THAT,
                        "Can't become a rat king when there are nearby cats or rat kings!");
            }
            MapInfo mapInfo = this.getMapInfo(curLoc);
            if (!mapInfo.isPassable()) {
                throw new GameActionException(CANT_DO_THAT,
                        "Can only upgrade if all squares in the 3x3 vicinity are passable");
            }
        }
        if (numAllyRats < 7) {
            throw new GameActionException(CANT_DO_THAT, "Not enough rats in the 3x3 square");
        }
    }

    @Override
    public boolean canBecomeRatKing() {
        try {
            assertCanBecomeRatKing();
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void becomeRatKing() throws GameActionException {
        canBecomeRatKing();
        int health = 0;
        for (Direction d : Direction.allDirections()) {
            InternalRobot currentRobot = this.gameWorld.getRobot(this.adjacentLocation(d));
            if (robot.getTeam() == currentRobot.getTeam()) {
                health += currentRobot.getHealth();
            }
            if (d != Direction.CENTER) {
                currentRobot.addHealth(-currentRobot.getHealth());
            }
        }
        this.gameWorld.getTeamInfo().addCheese(this.getTeam(), -GameConstants.RAT_KING_UPGRADE_CHEESE_COST);
        health = Math.min(health, UnitType.RAT_KING.health);
        robot.becomeRatKing(health);
        // TODO: becomeRatKing action?
    }

    // ***********************************
    // ****** COMMUNICATION METHODS ******
    // ***********************************

    @Override
    public void squeak(int messageContent) {
        Message message = new Message(messageContent, this.robot.getID(), this.gameWorld.getCurrentRound(),
                this.getLocation());
        this.gameWorld.squeak(this.robot, message);
        this.robot.incrementMessageCount();
    }

    @Override
    public Message[] readSqueaks(int roundNum) {
        ArrayList<Message> messages = new ArrayList<>();
        for (Message m : this.robot.getMessages()) {
            if (roundNum == -1 || m.getRound() == roundNum)
                messages.add(m);
        }
        return messages.toArray(new Message[messages.size()]);
    }

    @Override
    public void writeSharedArray(int index, int value) throws GameActionException {
        if (!this.getType().isRatKingType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rat kings can write to the shared array!");
        } else if (index < 0 || index >= GameConstants.SHARED_ARRAY_SIZE) {
            throw new GameActionException(CANT_DO_THAT, "Index " + index + " is out of bounds for the shared array!");
        } else if (value < 0 || value > GameConstants.COMM_ARRAY_MAX_VALUE) {
            throw new GameActionException(CANT_DO_THAT, "Value " + value + " is out of bounds for the shared array!");
        }

        this.gameWorld.writeSharedArray(index, value);
    }

    @Override
    public int readSharedArray(int index) throws GameActionException {
        return this.gameWorld.readSharedArray(index);
    }

    @Override
    public void writePersistentArray(int index, int value) throws GameActionException {
        if (!this.getType().isRatKingType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rat kings can write to the persistent array!");
        } else if (index < 0 || index >= GameConstants.PERSISTENT_ARRAY_SIZE) {
            throw new GameActionException(CANT_DO_THAT,
                    "Index " + index + " is out of bounds for the persistent array!");
        } else if (value < 0 || value > GameConstants.COMM_ARRAY_MAX_VALUE) {
            throw new GameActionException(CANT_DO_THAT,
                    "Value " + value + " is out of bounds for the persistent array!");
        }

        this.gameWorld.writePersistentArray(index, value);
    }

    @Override
    public int readPersistentArray(int index) throws GameActionException {
        return this.gameWorld.readPersistentArray(index);
    }

    // ***********************************
    // ****** OTHER ACTION METHODS *******
    // ***********************************

    private void assertCanTransferCheese(MapLocation loc, int amount) throws GameActionException {
        assertNotNull(loc);
        assertCanActLocation(loc, GameConstants.CHEESE_DROP_RADIUS_SQUARED);
        assertIsActionReady();
        InternalRobot robot = this.gameWorld.getRobot(loc);
        if (robot == null)
            throw new GameActionException(CANT_DO_THAT, "There is no robot at this location!");
        if (loc == this.robot.getLocation()) {
            throw new GameActionException(CANT_DO_THAT, "Cannot transfer cheese to yourself!");
        }
        if (amount == 0) {
            throw new GameActionException(CANT_DO_THAT, "Cannot transfer zero cheese!");
        }
        if (robot.getTeam() != this.robot.getTeam()) {
            throw new GameActionException(CANT_DO_THAT, "Cannot transfer resources to the enemy team!");
        }
        if (!this.robot.getType().isRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can transfer cheese!");
        }
        if (!robot.getType().isRatKingType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rat kings can receive cheese!");
        }
        if (amount < 0) {
            throw new GameActionException(CANT_DO_THAT, "Cheese can only be given, not taken!");
        }
        if (amount > this.robot.getCheese()) {
            throw new GameActionException(CANT_DO_THAT, "Cannot give more raw cheese than you currently have!");
        }
    }

    public boolean canTransferCheese(MapLocation loc, int amount) {
        try {
            assertCanTransferCheese(loc, amount);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    public void transferCheese(MapLocation loc, int amount) throws GameActionException {
        assertCanTransferCheese(loc, amount);
        this.robot.addCheese(-amount);
        InternalRobot robot = this.gameWorld.getRobot(loc);
        this.gameWorld.getTeamInfo().addCheese(getTeam(), amount);
        this.robot.addActionCooldownTurns(GameConstants.CHEESE_TRANSFER_COOLDOWN);
        this.gameWorld.getMatchMaker().addCheeseTransferAction(robot.getID(), amount);
    }

    public void assertCanThrowRat(Direction dir) throws GameActionException {
        assertIsActionReady();
        MapLocation nextLoc = this.getLocation().add(dir);
        if (!this.robot.getType().isRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can throw other rats!");
        }
        if (!this.robot.isCarryingRobot())
            throw new GameActionException(CANT_DO_THAT, "This rat is not carrying any rat!");
        if (!this.gameWorld.getGameMap().onTheMap(nextLoc)) {
            throw new RuntimeException("Cannot throw outside of map!");
        }
        if (!this.gameWorld.isPassable(nextLoc) || (this.gameWorld.getRobot(nextLoc) != null)) {
            throw new RuntimeException("There must be at least 1 empty space in front the throwing rat!");
        }
    }

    public boolean canThrowRat(Direction dir) {
        try {
            assertCanThrowRat(dir);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void throwRat(Direction dir) throws GameActionException {
        assertCanThrowRat(dir);
        this.robot.throwRobot(dir);
    }

    public void assertCanCarryRat(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        // must be senseable and within one square (adjacent)
        assertCanActLocation(loc, 1);
        assertIsActionReady();

        // Must be a rat-type
        if (!this.robot.getType().isRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can grab other rats!");
        }

        // adjacency
        if (!loc.isAdjacentTo(this.getLocation())) {
            throw new GameActionException(CANT_DO_THAT, "A rat can only grab adjacent robots!");
        }

        // must be in sight
        if (!this.canSenseLocation(loc)) {
            throw new GameActionException(CANT_DO_THAT, "A rat can only grab robots in front of it");
        }

        // can't already be carrying
        if (this.robot.isCarryingRobot()) {
            throw new GameActionException(CANT_DO_THAT, "Already carrying a rat");
        }

        // cannot grab while being carried
        if (this.robot.isGrabbedByRobot()) {
            throw new GameActionException(CANT_DO_THAT, "Cannot grab while being carried");
        }

        InternalRobot targetRobot = this.gameWorld.getRobot(loc);
        if (targetRobot == null) {
            throw new GameActionException(CANT_DO_THAT, "No robot at target location");
        }

        // target must be throwable (a unit that can be picked up)
        if (!targetRobot.getType().isThrowableType()) {
            throw new GameActionException(CANT_DO_THAT, "Target robot is not throwable");
        }

        if (targetRobot.isBeingThrown()) {
            throw new GameActionException(CANT_DO_THAT, "Target robot is currently being thrown");
        }

        // Allow grabbing if the target is facing away (cannot sense this robot), or
        // the target is allied, or the target is weaker (health comparison w/
        // threshold)
        boolean canGrab = false;
        if (!targetRobot.canSenseLocation(this.getLocation())) {
            canGrab = true;
        } else if (this.robot.getTeam() == targetRobot.getTeam()) {
            canGrab = true;
        } else if (targetRobot.getHealth() + GameConstants.HEALTH_GRAB_THRESHOLD < this.robot.getHealth()) {
            canGrab = true;
        }

        if (!canGrab) {
            throw new GameActionException(CANT_DO_THAT, "Cannot grab that robot");
        }
    }

    @Override
    public boolean canCarryRat(MapLocation loc) {
        try {
            assertCanCarryRat(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void carryRat(MapLocation loc) throws GameActionException {
        assertCanCarryRat(loc);
        this.robot.grabRobot(loc);
    }

    @Override
    public void disintegrate() {
        throw new RobotDeathException();
    }

    @Override
    public void resign() {
        Team team = getTeam();
        gameWorld.getObjectInfo().eachRobot((robot) -> {
            if (robot.getTeam() == team) {
                gameWorld.destroyRobot(robot.getID());
            }
            return true;
        });
        gameWorld.setWinner(team.opponent(), DominationFactor.RESIGNATION);
    }

    // ***********************************
    // ******** DEBUG METHODS ************
    // ***********************************

    @Override
    public void setIndicatorString(String string) {
        if (string.length() > GameConstants.INDICATOR_STRING_MAX_LENGTH) {
            string = string.substring(0, GameConstants.INDICATOR_STRING_MAX_LENGTH);
        }
        this.robot.setIndicatorString(string);
    }

    @Override
    public void setIndicatorDot(MapLocation loc, int red, int green, int blue) throws GameActionException {
        assertNotNull(loc);
        if (!this.gameWorld.getGameMap().onTheMap(loc))
            throw new GameActionException(CANT_DO_THAT, "Indicator dots should have map locations on the map!");
        this.gameWorld.getMatchMaker().addIndicatorDot(getID(), loc, red, green, blue);
    }

    @Override
    public void setIndicatorLine(MapLocation startLoc, MapLocation endLoc, int red, int green, int blue)
            throws GameActionException {
        assertNotNull(startLoc);
        assertNotNull(endLoc);
        if (!this.gameWorld.getGameMap().onTheMap(startLoc))
            throw new GameActionException(CANT_DO_THAT, "Indicator lines should have map locations on the map!");
        if (!this.gameWorld.getGameMap().onTheMap(endLoc))
            throw new GameActionException(CANT_DO_THAT, "Indicator lines should have map locations on the map!");
        this.gameWorld.getMatchMaker().addIndicatorLine(getID(), startLoc, endLoc, red, green, blue);
    }

    @Override
    public void setTimelineMarker(String label, int red, int green, int blue) {
        if (label.length() > GameConstants.TIMELINE_LABEL_MAX_LENGTH) {
            label = label.substring(0, GameConstants.TIMELINE_LABEL_MAX_LENGTH);
        }
        this.gameWorld.getMatchMaker().addTimelineMarker(this.getTeam(), label, red, green, blue);
    }

}
