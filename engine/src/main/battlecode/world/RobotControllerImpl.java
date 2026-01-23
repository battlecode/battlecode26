package battlecode.world;

import battlecode.common.*;

import static battlecode.common.GameActionExceptionType.*;
import battlecode.instrumenter.RobotDeathException;

import java.util.*;
import java.util.stream.Collectors;

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

    // private int locationToInt(MapLocation loc) {
    // return this.gameWorld.locationToIndex(loc);
    // }

    private MapInfo getMapInfo(MapLocation loc) throws GameActionException {
        Team team = this.getTeam();
        GameWorld gw = this.gameWorld;
        Trap trap = gw.getTrap(loc, team);
        TrapType trapType = (trap != null) ? trap.getType() : TrapType.NONE;
        RobotInfo flyingRobot = gw.getFlyingRobot(loc)!=null ? gw.getFlyingRobot(loc).getRobotInfo() : null;
        MapInfo currentLocInfo = new MapInfo(loc, gw.isPassable(loc), flyingRobot, gw.getWall(loc), gw.getDirt(loc),
                gw.getCheeseAmount(loc), trapType,
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

    @Override
    public boolean isCooperation() {
        return this.gameWorld.isCooperation();
    }

    @Override
    public Team getBackstabbingTeam() {
        return this.gameWorld.getBackStabbingTeam();
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
    public MapLocation[] getAllPartLocations() {
        return this.robot.getAllPartLocations();
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
    public int getNumberCatTraps(){
        return this.gameWorld.getTrapCount(TrapType.CAT_TRAP, this.getTeam());
    }

    @Override 
    public int getNumberRatTraps(){
        return this.gameWorld.getTrapCount(TrapType.RAT_TRAP, this.getTeam());
    }

    @Override
    public UnitType getType() {
        return this.robot.getType();
    }

    @Override
    public RobotInfo getCarrying() {
        if (!this.robot.isCarryingRobot())
            return null;
        else
            return this.robot.getRobotBeingCarried().getRobotInfo();
    }

    @Override
    public boolean isBeingThrown() {
        return this.robot.isBeingThrown();
    }

    @Override
    public boolean isBeingCarried() {
        return this.robot.isGrabbedByRobot();
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

    private void assertCanActLocation(MapLocation loc, float maxRadiusSquared) throws GameActionException {
        // assumes maxRadiusSquared <= visionRadiusSquared.
        // This handles the angle checking, so we only check distance.
        assertCanSenseLocation(loc);
        float distance = (this.getType().usesBottomLeftLocationForDistance())
                ? (getLocation().bottomLeftDistanceSquaredTo(loc))
                : (getLocation().distanceSquaredTo(loc));

        // float addDistance = (float) Math.ceil((this.getType().size / (2.0) +
        // Math.sqrt((double) maxRadiusSquared))
        // * (this.getType().size / 2.0 + Math.sqrt((double) maxRadiusSquared)));
        float addDistance = maxRadiusSquared;

        if (distance > addDistance)
            throw new GameActionException(OUT_OF_RANGE,
                    "Target location not within action range");
    }

    // private void assertCanActOffCenterLocation(MapLocation loc, int
    // maxRadiusSquared) throws GameActionException {
    // assertNotNull(loc);
    // if (getLocation().bottomLeftDistanceSquaredTo(loc) > maxRadiusSquared)
    // throw new GameActionException(OUT_OF_RANGE,
    // "Target location not within action range");
    // if (!this.gameWorld.getGameMap().onTheMap(loc))
    // throw new GameActionException(CANT_SENSE_THAT,
    // "Target location is not on the map");
    // }

    private void assertCanPlaceDirt(MapLocation loc) throws GameActionException {
        UnitType myType = this.robot.getType();

        assertIsActionReady();
        assertIsRobotType(myType);
        float myBuildRadiusSquared = myType == UnitType.RAT_KING ? GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED : (myType == UnitType.CAT ? GameConstants.CAT_BUILD_DISTANCE_SQUARED : GameConstants.BUILD_DISTANCE_SQUARED);
        assertCanActLocation(loc, myBuildRadiusSquared);

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
        if (this.gameWorld.hasCheeseMine(loc))
            throw new GameActionException(CANT_DO_THAT, "Tile has a cheese mine!");
    }

    private void assertCanRemoveDirt(MapLocation loc) throws GameActionException {
        UnitType myType = this.robot.getType();

        assertIsActionReady();
        assertIsRobotType(myType);

        float myBuildRadiusSquared = myType == UnitType.RAT_KING ? GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED : (myType == UnitType.CAT ? GameConstants.CAT_BUILD_DISTANCE_SQUARED : GameConstants.BUILD_DISTANCE_SQUARED);
        assertCanActLocation(loc, myBuildRadiusSquared);

        if ((this.robot.getType().isBabyRatType()
                || this.robot.getType().isRatKingType()) && (this.getAllCheese() < GameConstants.DIG_DIRT_CHEESE_COST))
            throw new GameActionException(CANT_DO_THAT, "Insufficient cheese to remove dirt!");

        if (!this.gameWorld.getDirt(loc))
            throw new GameActionException(CANT_DO_THAT, "No dirt to remove at that location!");

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
    public void placeDirt(MapLocation loc) throws GameActionException {
        assertCanPlaceDirt(loc);
        this.gameWorld.setDirt(loc, true);
        this.gameWorld.getTeamInfo().updateDirt(this.robot.getTeam(), true);
        this.robot.addCheese(-1 * GameConstants.PLACE_DIRT_CHEESE_COST);

        this.robot.addActionCooldownTurns(GameConstants.DIG_COOLDOWN);
        this.gameWorld.getMatchMaker().addPlaceDirtAction(loc);
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

    private void assertCanRemoveRatTrap(MapLocation loc) throws GameActionException {
        UnitType myType = this.robot.getType();
        Team myTeam = this.getTeam();

        assertIsRobotType(myType);
        assertCanActLocation(loc, myType == UnitType.RAT_KING
                ? GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED
                : GameConstants.BUILD_DISTANCE_SQUARED);

        if (!this.gameWorld.hasRatTrap(loc, myTeam)) {
            throw new GameActionException(CANT_DO_THAT, "No rat trap to remove at that location!");
        }
    }

    private void assertCanPlaceTrap(MapLocation loc, TrapType trapType) throws GameActionException {
        UnitType myType = this.robot.getType();

        assertIsActionReady();
        assertIsRobotType(myType);
        assertCanActLocation(loc, myType == UnitType.RAT_KING
                ? GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED
                : GameConstants.BUILD_DISTANCE_SQUARED);

        if (trapType == TrapType.CAT_TRAP && !this.gameWorld.catTrapsAllowed(this.getTeam()))
            throw new GameActionException(CANT_DO_THAT, "Can't place new cat traps in backstabbing mode unless you were backstabbed and within " + GameConstants.CAT_TRAP_ROUNDS_AFTER_BACKSTAB + " rounds!");
        if (!this.gameWorld.isPassable(loc))
            throw new GameActionException(CANT_DO_THAT, "Can't place trap on a wall or dirt!");
        if (this.gameWorld.getRobot(loc) != null)
            throw new GameActionException(CANT_DO_THAT, "Can't place trap on an occupied tile!");
        if (this.gameWorld.hasTrap(loc, this.robot.getTeam()))
            throw new GameActionException(CANT_DO_THAT, "Tile already has a trap!");
        if (this.gameWorld.getTrapCount(trapType, this.robot.getTeam()) >= trapType.maxCount)
            throw new GameActionException(CANT_DO_THAT,
                    "Team has reached maximum number of " + trapType + " traps on the map!");
        if (getAllCheese() < trapType.buildCost) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to build trap!");
        }
        if (this.gameWorld.hasCheeseMine(loc))
            throw new GameActionException(CANT_DO_THAT, "Tile has a cheese mine!");
    }

    private void assertCanRemoveCatTrap(MapLocation loc) throws GameActionException {
        UnitType myType = this.robot.getType();

        assertIsRobotType(myType);
        assertCanActLocation(loc, myType == UnitType.RAT_KING
                ? GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED
                : GameConstants.BUILD_DISTANCE_SQUARED);

        if (!this.gameWorld.hasCatTrap(loc, this.getTeam())) {
            throw new GameActionException(CANT_DO_THAT, "No cat trap to remove at that location!");
        }
    }

    @Override
    public boolean canPlaceRatTrap(MapLocation loc) {
        try {
            assertCanPlaceTrap(loc, TrapType.RAT_TRAP);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void placeRatTrap(MapLocation loc) throws GameActionException {
        assertCanPlaceTrap(loc, TrapType.RAT_TRAP);
        buildTrap(TrapType.RAT_TRAP, loc);
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
        Team team = this.getTeam();
        assertCanRemoveRatTrap(loc);
        Trap trap = this.gameWorld.getTrap(loc, team);
        this.gameWorld.removeTrap(loc, team);
        this.gameWorld.getMatchMaker().addRemoveTrapAction(trap.getLocation(), trap.getTeam());
    }

    @Override
    public boolean canPlaceCatTrap(MapLocation loc) {
        try {
            assertCanPlaceTrap(loc, TrapType.CAT_TRAP);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void placeCatTrap(MapLocation loc) throws GameActionException {
        assertCanPlaceTrap(loc, TrapType.CAT_TRAP);
        buildTrap(TrapType.CAT_TRAP, loc);
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
        Team team = this.getTeam();
        assertCanRemoveCatTrap(loc);
        Trap trap = this.gameWorld.getTrap(loc, team);
        this.gameWorld.removeTrap(loc, team);
        this.gameWorld.getMatchMaker().addRemoveTrapAction(trap.getLocation(), trap.getTeam());
    }

    @Override
    public void removeDirt(MapLocation loc) throws GameActionException {
        assertCanRemoveDirt(loc);
        this.gameWorld.setDirt(loc, false);
        this.gameWorld.getTeamInfo().updateDirt(this.robot.getTeam(), false);
        if (this.robot.getType().isBabyRatType() || this.robot.getType().isRatKingType())
            this.robot.addCheese(-1 * GameConstants.DIG_DIRT_CHEESE_COST);

        this.robot.addActionCooldownTurns(GameConstants.DIG_COOLDOWN);
        this.gameWorld.getMatchMaker().addRemoveDirtAction(loc);
    }

    private void assertCanPickUpCheese(MapLocation loc) throws GameActionException {
        UnitType myType = this.robot.getType();
        assertIsRobotType(myType);
        assertCanActLocation(loc, GameConstants.CHEESE_PICK_UP_RADIUS_SQUARED);

        if (this.gameWorld.getCheeseAmount(loc) <= 0) {
            throw new GameActionException(CANT_DO_THAT, "No cheese at this location!");
        }

        if (myType != UnitType.BABY_RAT && myType != UnitType.RAT_KING) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can pick up cheese");
        }

        if (this.robot.isBeingThrown()){
            throw new GameActionException(CANT_DO_THAT, "Flying rats cannot pick up cheese");
        }
    }

    @Override
    public boolean canPickUpCheese(MapLocation loc) {
        try {
            assertCanPickUpCheese(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void pickUpCheese(MapLocation loc) throws GameActionException {
        assertCanPickUpCheese(loc);
        int amountCheeseAvail = this.gameWorld.getCheeseAmount(loc);
        this.gameWorld.addCheese(loc, -amountCheeseAvail);
        this.robot.addCheese(amountCheeseAvail);
        this.gameWorld.getMatchMaker().addCheesePickUpAction(loc);
        this.gameWorld.getTeamInfo().addCheeseCollected(getTeam(), amountCheeseAvail);

        if (getType() == UnitType.RAT_KING) {
            this.gameWorld.getMatchMaker().addCheeseTransferAction(robot.getID(), amountCheeseAvail);
            this.gameWorld.getTeamInfo().addCheeseTransferred(getTeam(), amountCheeseAvail);
        }
    }

    @Override
    public void pickUpCheese(MapLocation loc, int pickUpAmount) throws GameActionException {
        assertCanPickUpCheese(loc);
        int amountCheeseAvail = this.gameWorld.getCheeseAmount(loc);

        // bound pickup amount above by the amount of cheese available and below by 0
        // (can't pick up negative amounts of cheese)
        pickUpAmount = Math.max(Math.min(pickUpAmount, amountCheeseAvail), 0);

        this.gameWorld.addCheese(loc, -pickUpAmount);
        this.robot.addCheese(pickUpAmount);
        this.gameWorld.getMatchMaker().addCheesePickUpAction(loc);
        this.gameWorld.getTeamInfo().addCheeseCollected(getTeam(), pickUpAmount);

        if (getType() == UnitType.RAT_KING) {
            this.gameWorld.getMatchMaker().addCheeseTransferAction(robot.getID(), pickUpAmount);
            this.gameWorld.getTeamInfo().addCheeseTransferred(getTeam(), pickUpAmount);
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
        Boolean isFlying = sensedRobot.isBeingThrown();
        return sensedRobot != null && isFlying && canSenseLocation(sensedRobot.getLocation());
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

        InternalRobot[] allSensedRobots = gameWorld.getAllRobotsWithinRadiusSquared(center, actualRadiusSquared, team,
                this.robot.getChirality());
        List<RobotInfo> validSensedRobots = new ArrayList<>();
        HashSet<Integer> uniqueRobotIds = new HashSet<>();
        for (InternalRobot sensedRobot : allSensedRobots) {
            if (uniqueRobotIds.contains(sensedRobot.getID()))
                continue;
            // check if this robot
            if (sensedRobot.equals(this.robot))
                continue;
            // check if can sense in vision cone (restricted radius)
            boolean canSensePartOfRobot = false;
            for (MapLocation robotpart : sensedRobot.getAllPartLocations()) {
                canSensePartOfRobot = canSensePartOfRobot || (canSenseLocation(robotpart)
                        && center.isWithinDistanceSquared(robotpart, actualRadiusSquared));
            }

            if (!canSensePartOfRobot)
                continue;

            // check if right team
            if (team != null && sensedRobot.getTeam() != team)
                continue;
            validSensedRobots.add(sensedRobot.getRobotInfo());
            uniqueRobotIds.add(sensedRobot.getID());
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
        int actualRadiusSquared = radiusSquared == -1 ? this.getType().visionConeRadiusSquared
                : Math.min(radiusSquared, this.getType().visionConeRadiusSquared);
        MapLocation[] allSensedLocs = gameWorld.getAllLocationsWithinRadiusSquared(center,
                actualRadiusSquared, this.robot.getChirality()); // expand slightly
                                                                 // to allow
                                                                 // off-center
                                                                 // sensing
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
        MapLocation[] possibleLocs = gameWorld.getAllLocationsWithinRadiusSquared(center,
                actualRadiusSquared, this.robot.getChirality());
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
        if (this.robot.isBeingThrown())
            throw new GameActionException(IS_NOT_READY,
                    "This robot is currently being thrown!");
        if (this.robot.isGrabbedByRobot())
            throw new GameActionException(IS_NOT_READY,
                    "This robot is currently being carried!");
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
        if (this.robot.isBeingThrown())
            throw new GameActionException(IS_NOT_READY,
                    "This robot is currently being thrown!");
        if (this.robot.isGrabbedByRobot())
            throw new GameActionException(IS_NOT_READY,
                    "This robot is currently being carried!");
    }

    private void assertIsTurningReady() throws GameActionException {
        if (!this.robot.canTurnCooldown())
            throw new GameActionException(IS_NOT_READY,
                    "This robot's turning cooldown has not expired.");
        if (this.robot.isBeingThrown())
            throw new GameActionException(IS_NOT_READY,
                    "This robot is currently being thrown!");
        if (this.robot.isGrabbedByRobot())
            throw new GameActionException(IS_NOT_READY,
                    "This robot is currently being carried!");
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
    public boolean isTurningReady() {
        try {
            assertIsTurningReady();
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public int getMovementCooldownTurns() {
        return this.robot.getMovementCooldownTurns();
    }

    @Override
    public int getTurningCooldownTurns() {
        return this.robot.getTurningCooldownTurns();
    }

    // ***********************************
    // ****** MOVEMENT METHODS ***********
    // ***********************************

    private void assertCanMoveForward() throws GameActionException {
        assertCanMove(robot.getDirection());
    }

    private void assertCanMove(Direction d) throws GameActionException {
        assertIsMovementReady();
        MapLocation[] curLocs = robot.getAllPartLocations();

        MapLocation[] newLocs = new MapLocation[curLocs.length];
        for (int i = 0; i < newLocs.length; i++) {

            newLocs[i] = curLocs[i].add(d);
        }

        for (MapLocation loc : newLocs) {
            if (!onTheMap(loc)) {
                throw new GameActionException(OUT_OF_RANGE,
                        "Can only move to locations on the map; " + loc + " is not on the map. Currently at location "
                                + this.getLocation());
            }

            InternalRobot occupyingRobot = this.gameWorld.getRobot(loc);

            if ((occupyingRobot != null) && (occupyingRobot.getID() != this.robot.getID())
                    && !(occupyingRobot.getType().isBabyRatType() && this.getType().isCatType())) {
                throw new GameActionException(CANT_MOVE_THERE,
                        "Cannot move to an occupied location; " + loc + " is occupied by a different robot.");
            }

            if (!this.gameWorld.isPassable(loc)) {
                throw new GameActionException(CANT_MOVE_THERE,
                        "Cannot move to an impassable location; " + loc + " is impassable.");
            }

        }
    }

    @Override
    public boolean canMoveForward() {
        try {
            assertCanMoveForward();
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public boolean canMove(Direction d) {
        try {
            assertCanMove(d);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void moveForward() throws GameActionException {
        move(robot.getDirection());
    }

    public void processTrapsAtLocation(MapLocation loc) {
        // process any traps at newly entered location

        // add trap triggers in game world
        for (int j = this.gameWorld.getTrapTriggers(loc).size() - 1; j >= 0; j--) {
            Trap trap = this.gameWorld.getTrapTriggers(loc).get(j);
            TrapType type = trap.getType();
            boolean wrongTrapType = ((this.getType().isBabyRatType() || this.getType().isRatKingType())
                    && type == TrapType.CAT_TRAP)
                    || (this.getType().isCatType() && type == TrapType.RAT_TRAP);

            if (trap.getTeam() == this.robot.getTeam() || wrongTrapType) {
                continue;
            }

            this.gameWorld.triggerTrap(trap, robot);

        }

    }

    @Override
    public void move(Direction d) throws GameActionException {
        assertCanMove(d);

        // calculate set of next map locations
        MapLocation[] curLocs = robot.getAllPartLocations();
        for (int i = 0; i < curLocs.length; i++) {
            MapLocation newLoc = curLocs[i].add(d);
            InternalRobot crushedRobot = this.gameWorld.getRobot(newLoc);
            if (crushedRobot != null && this.getID() != crushedRobot.getID() && this.getType().isCatType()
                    && crushedRobot.getType().isBabyRatType()) {
                // kill this rat
                if (crushedRobot.isCarryingRobot()){
                    InternalRobot carriedRobot = crushedRobot.getRobotBeingCarried();
                    carriedRobot.addHealth(-carriedRobot.getHealth());
                }
                crushedRobot.addHealth(-crushedRobot.getHealth());
            }
            // processTrapsAtLocation(newLoc);
        }

        this.robot.translateLocation(d.dx, d.dy);

        for (int i = 0; i < curLocs.length; i++) {
            MapLocation newLoc = curLocs[i].add(d);
            processTrapsAtLocation(newLoc);
        }

        this.robot.addMovementCooldownTurns(d);
    }

    private void assertCanTurn() throws GameActionException {
        assertIsTurningReady();
    }

    private void assertCanTurn(Direction d) throws GameActionException {
        assertIsTurningReady();

        if (d == null) {
            throw new GameActionException(CANT_DO_THAT, "Direction to turn to is null!");
        }

        if (d == Direction.CENTER) {
            throw new GameActionException(CANT_DO_THAT, "Cannot turn to CENTER direction!");
        }
    }

    @Override
    public boolean canTurn() {
        try {
            assertCanTurn();
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public boolean canTurn(Direction d) {
        try {
            assertCanTurn(d);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void turn(Direction d) throws GameActionException {
        assertCanTurn(d);

        this.robot.setDirection(d);
        this.robot.addTurningCooldownTurns();
    }

    // ***********************************
    // ******** BUILDING METHODS *********
    // ***********************************

    @Override
    public int getCurrentRatCost() {
        return GameConstants.BUILD_ROBOT_BASE_COST +
                GameConstants.BUILD_ROBOT_COST_INCREASE * (this.gameWorld.getTeamInfo().getNumBabyRats(getTeam())
                        / GameConstants.NUM_ROBOTS_FOR_COST_INCREASE);
    }

    private void assertIsRobotType(UnitType type) throws GameActionException {
        if (!type.isRobotType()) {
            throw new GameActionException(CANT_DO_THAT, "Given type " + type + " is not a robot type!");
        }
    }

    private void assertCanBuildRat(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        assertCanActLocation(loc, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        assertIsActionReady();

        if (!this.robot.getType().isRatKingType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rat kings can spawn other rats!");
        }

        int cost = getCurrentRatCost();

        if (this.gameWorld.getTeamInfo().getCheese(this.robot.getTeam()) < cost) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to build new rat!");
        }

        if (isLocationOccupied(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Location is already occupied!");
        }

        if (!sensePassability(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Location has dirt or a wall!");
        }
    }

    @Override
    public boolean canBuildRat(MapLocation loc) {
        try {
            assertCanBuildRat(loc);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void buildRat(MapLocation loc) throws GameActionException {
        assertCanBuildRat(loc);
        int cost = getCurrentRatCost();
        this.robot.addCheese(-cost);
        this.robot.addActionCooldownTurns(GameConstants.BUILD_ROBOT_COOLDOWN);
        this.gameWorld.spawnRobot(UnitType.BABY_RAT, loc, this.getDirection(), this.robot.getChirality(),
                this.robot.getTeam());
        InternalRobot robotSpawned = this.gameWorld.getRobot(loc);
        this.gameWorld.getMatchMaker().addSpawnAction(robotSpawned.getID(), loc, this.robot.getDirection(),
                this.robot.getChirality(), getTeam(), UnitType.BABY_RAT);
    }

    public void buildTrap(TrapType type, MapLocation loc) throws GameActionException {
        this.robot.addActionCooldownTurns(type.actionCooldown);
        this.robot.addCheese(-type.buildCost);

        Team team = this.getTeam();
        int trapId = this.gameWorld.idGenerator.nextID();
        Trap newTrap = new Trap(loc, type, team, trapId);

        this.gameWorld.placeTrap(loc, newTrap);
        this.gameWorld.getMatchMaker().addPlaceTrapAction(trapId, loc, team, type);
    }

    // *****************************
    // ****** ATTACK / HEAL ********
    // *****************************

    private void assertCanAttackRat(MapLocation loc, int cheeseConsumed) throws GameActionException {
        assertIsActionReady();
        UnitType myType = this.getType();
        // Attack is limited to vision radius
        assertCanActLocation(loc, myType.getVisionRadiusSquared());

        MapLocation myLoc = this.getLocation();
        int distSq = myLoc.distanceSquaredTo(loc);

        if (distSq == 0 || distSq > (myType == UnitType.RAT_KING
                ? GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED
                : GameConstants.ATTACK_DISTANCE_SQUARED)) {
            throw new GameActionException(CANT_DO_THAT, "Rats can only attack adjacent squares!");
        }

        if (!this.gameWorld.isPassable(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Rats cannot attack squares with walls or dirt on them!");
        }

        if (this.getAllCheese() < cheeseConsumed) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to bite!");
        }

        if (this.getType() == UnitType.CAT) {
            throw new GameActionException(CANT_DO_THAT, "Unit must be a baby rat or rat king to bite!");
        }

        if (cheeseConsumed < 0) {
            throw new GameActionException(CANT_DO_THAT, "Cheese consumed must be non-negative!");
        }

        InternalRobot enemyRobot = this.gameWorld.getRobot(loc);

        if (enemyRobot == null) {
            throw new GameActionException(CANT_DO_THAT, "No robot to attack at the specified location!");
        }

        if (enemyRobot.getTeam() == this.robot.getTeam()) {
            throw new GameActionException(CANT_DO_THAT, "Cannot attack ally robots!");
        }
    }

    private void assertCanAttackCat(MapLocation loc) throws GameActionException {
        assertIsActionReady();
        assertCanActLocation(loc, this.getType().getVisionRadiusSquared());

        if (!this.gameWorld.isPassable(loc)) {
            throw new GameActionException(CANT_DO_THAT, "Cats cannot attack squares with walls or dirt on them!");
        }

        InternalRobot enemyRobot = this.gameWorld.getRobot(loc);

        if (enemyRobot == null) {
            throw new GameActionException(CANT_DO_THAT, "No robot to attack at the specified location!");
        }

        if (enemyRobot != null && enemyRobot.getTeam() == this.robot.getTeam()) {
            throw new GameActionException(CANT_DO_THAT, "Cannot attack another cat!");
        }
    }

    private void assertCanAttack(MapLocation loc, int cheeseConsumed) throws GameActionException {
        if (loc == null) {
            throw new GameActionException(CANT_DO_THAT, "Robot units must specify a location to attack");
        }

        switch (this.robot.getType()) {
            case BABY_RAT, RAT_KING:
                assertCanAttackRat(loc, cheeseConsumed);
                break;
            case CAT:
                assertCanAttackCat(loc);
                break;
            default:
                assertCanAttackRat(loc, cheeseConsumed);
                break;
        }
    }

    @Override
    public boolean canAttack(MapLocation loc) {
        try {
            assertCanAttack(loc, 0);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public boolean canAttack(MapLocation loc, int cheeseConsumed) {
        try {
            assertCanAttack(loc, cheeseConsumed);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void attack(MapLocation loc) throws GameActionException {
        assertCanAttack(loc, 0);
        if (this.robot.getType().isRobotType())
            this.robot.addActionCooldownTurns(this.robot.getType().actionCooldown);
        this.robot.attack(loc);
    }

    @Override
    public void attack(MapLocation loc, int cheese) throws GameActionException {
        assertCanAttack(loc, cheese);
        if (this.robot.getCheese() + this.gameWorld.getTeamInfo().getCheese(this.robot.getTeam()) < cheese) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to attack!");
        }
        if (this.robot.getType().isRobotType())
            this.robot.addActionCooldownTurns(this.robot.getType().actionCooldown);
        this.robot.attack(loc, cheese);
    }

    public void assertCanBecomeRatKing() throws GameActionException {
        assertIsActionReady();
        TeamInfo teamInfo = this.gameWorld.getTeamInfo();

        if (teamInfo.getCheese(this.robot.getTeam()) < GameConstants.RAT_KING_UPGRADE_CHEESE_COST) {
            throw new GameActionException(CANT_DO_THAT, "Not enough cheese to upgrade to a rat king");
        }

        int numRatKings = teamInfo.getNumRatKings(this.robot.getTeam());

        if (numRatKings >= GameConstants.MAX_NUMBER_OF_RAT_KINGS) {
            throw new GameActionException(CANT_DO_THAT,
                    "Cannot have more than " + GameConstants.MAX_NUMBER_OF_RAT_KINGS + " rat kings per team!");
        }

        if (numRatKings >= GameConstants.MAX_NUMBER_OF_RAT_KINGS_AFTER_CUTOFF
                && this.gameWorld.getCurrentRound() > GameConstants.RAT_KING_CUTOFF_ROUND) {
            throw new GameActionException(CANT_DO_THAT,
                    "Cannot make a new rat king when your team has at least "
                            + GameConstants.MAX_NUMBER_OF_RAT_KINGS_AFTER_CUTOFF
                            + " rat kings after round " + GameConstants.RAT_KING_CUTOFF_ROUND + "!");
        }

        int numAllyRats = 0;

        for (Direction d : Direction.allDirections()) {
            MapLocation curLoc = this.adjacentLocation(d);

            if (!onTheMap(curLoc)) {
                throw new GameActionException(CANT_DO_THAT,
                        "Can't become a rat king when the 3x3 vicinity goes off the map!");
            }

            InternalRobot curRobot = this.gameWorld.getRobot(curLoc);

            if (curRobot != null && curRobot.getTeam() == this.robot.getTeam()
                    && curRobot.getType() == UnitType.BABY_RAT) {
                numAllyRats += 1;
            }

            if (curRobot != null && !curRobot.getType().isBabyRatType()) {
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
        assertCanBecomeRatKing();
        int health = 0;

        for (Direction d : Direction.allDirections()) {
            InternalRobot currentRobot = this.gameWorld.getRobot(this.adjacentLocation(d));

            if (currentRobot != null && robot.getTeam() == currentRobot.getTeam()) {
                health += currentRobot.getHealth();
            }

            if (currentRobot != null && d != Direction.CENTER) {
                // all their raw cheese is taken
                this.gameWorld.getTeamInfo().addCheese(this.getTeam(), currentRobot.getCheese());
                currentRobot.addCheese(-currentRobot.getCheese());

                if (currentRobot.isCarryingRobot()) {
                    // you steal the cheese of the robot they are carrying if it's an enemy,
                    // or just add it to your global cheese if it's an ally
                    InternalRobot robotBeingCarried = currentRobot.getRobotBeingCarried();
                    this.gameWorld.getTeamInfo().addCheese(this.getTeam(), robotBeingCarried.getCheese());
                    robotBeingCarried.addCheese(-robotBeingCarried.getCheese());
                    robotBeingCarried.addHealth(-robotBeingCarried.getHealth());
                }

                // all robots in the 3x3 including enemies die
                currentRobot.addHealth(-currentRobot.getHealth());
            }
            this.gameWorld.addRobot(this.adjacentLocation(d), this.robot);
        }

        if (this.robot.isCarryingRobot()) {
            InternalRobot robotBeingCarried = this.robot.getRobotBeingCarried();
            robotBeingCarried.addHealth(-robotBeingCarried.getHealth());
        }

        this.gameWorld.getTeamInfo().addCheese(this.getTeam(), -GameConstants.RAT_KING_UPGRADE_CHEESE_COST);
        health = Math.min(health, UnitType.RAT_KING.health);

        this.gameWorld.getTeamInfo().addCheese(this.getTeam(), robot.getCheese());
        this.robot.addCheese(-this.robot.getCheese());
        this.robot.becomeRatKing(health);

        for (Direction d : Direction.allDirections()) {
            if (d != Direction.CENTER) {
                MapLocation newLoc = this.adjacentLocation(d);
                processTrapsAtLocation(newLoc);
            }
        }

        this.gameWorld.getMatchMaker().addBecomeRatKingAction(this.getID());
        this.gameWorld.getTeamInfo().addRatKings(1, getTeam());
    }

    // ***********************************
    // ****** COMMUNICATION METHODS ******
    // ***********************************

    @Override
    public boolean squeak(int messageContent) {
        if (this.robot.getSentMessagesCount() >= GameConstants.MAX_MESSAGES_SENT_ROBOT)
            return false;
        Message message = new Message(messageContent, this.robot.getID(), this.gameWorld.getCurrentRound(),
                this.getLocation());
        this.gameWorld.squeak(this.robot, message);
        this.robot.incrementMessageCount();
        return true;
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

        this.gameWorld.writeSharedArray(index, value, this.getTeam());
    }

    @Override
    public int readSharedArray(int index) throws GameActionException {
        return this.gameWorld.readSharedArray(index, this.getTeam());
    }

    public void writePersistentArray(int index, int value) throws GameActionException {
        if (!this.getType().isRatKingType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rat kings can write to the persistent array!");
        } else if (value < 0 || value > GameConstants.COMM_ARRAY_MAX_VALUE) {
            throw new GameActionException(CANT_DO_THAT,
                    "Value " + value + " is out of bounds for the persistent array!");
        }

        this.gameWorld.writePersistentArray(index, value, this.getTeam());
    }

    public int readPersistentArray(int index) throws GameActionException {
        return this.gameWorld.readPersistentArray(index, this.getTeam());
    }

    // ***********************************
    // ****** OTHER ACTION METHODS *******
    // ***********************************

    private void assertCanTransferCheese(MapLocation loc, int amount) throws GameActionException {
        assertNotNull(loc);
        assertCanActLocation(loc, GameConstants.CHEESE_TRANSFER_RADIUS_SQUARED);
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
        if (!this.robot.getType().isBabyRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only baby rats can transfer cheese!");
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

    @Override
    public void transferCheese(MapLocation loc, int amount) throws GameActionException {
        assertCanTransferCheese(loc, amount);
        this.robot.addCheese(-amount);
        InternalRobot robot = this.gameWorld.getRobot(loc);
        this.gameWorld.getTeamInfo().addCheese(getTeam(), amount);
        this.gameWorld.getTeamInfo().addCheeseTransferred(getTeam(), amount);
        this.robot.addActionCooldownTurns(GameConstants.CHEESE_TRANSFER_COOLDOWN);
        this.gameWorld.getMatchMaker().addCheeseTransferAction(robot.getID(), amount);
    }

    public void assertCanThrowRat(Direction dir) throws GameActionException {
        assertIsActionReady();
        MapLocation nextLoc = this.getLocation().add(dir);

        if (!this.robot.getType().isBabyRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can throw other rats!");
        }
        if (!this.robot.isCarryingRobot())
            throw new GameActionException(CANT_DO_THAT, "This rat is not carrying any rat!");
        if (!this.gameWorld.getGameMap().onTheMap(nextLoc)) {
            throw new GameActionException(CANT_DO_THAT, "Cannot throw outside of map!");
        }
        if (!this.gameWorld.isPassable(nextLoc) || (this.gameWorld.getRobot(nextLoc) != null)) {
            throw new GameActionException(CANT_DO_THAT,
                    "There must be at least 1 empty space in front the throwing rat!");
        }
    }

    public void assertCanDropRat(Direction dir) throws GameActionException {
        assertIsActionReady();
        MapLocation nextLoc = this.getLocation().add(dir);

        if (!this.robot.getType().isBabyRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can drop other rats!");
        }
        if (!this.robot.isCarryingRobot())
            throw new GameActionException(CANT_DO_THAT, "This rat is not carrying any rat!");
        if (!this.gameWorld.getGameMap().onTheMap(nextLoc)) {
            throw new GameActionException(CANT_DO_THAT, "Cannot drop outside of map!");
        }
        if (!this.gameWorld.isPassable(nextLoc) || (this.gameWorld.getRobot(nextLoc) != null)) {
            throw new GameActionException(CANT_DO_THAT, "Can only drop rats into empty spaces!");
        }
    }

    public boolean canThrowRat() {
        try {
            assertCanThrowRat(this.robot.getDirection());
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void throwRat() throws GameActionException {
        assertCanThrowRat(this.robot.getDirection());
        this.robot.addActionCooldownTurns(GameConstants.THROW_RAT_COOLDOWN);
        this.robot.throwRobot();
    }

    public boolean canDropRat(Direction dir) {
        try {
            assertCanDropRat(dir);
            return true;
        } catch (GameActionException e) {
            return false;
        }
    }

    @Override
    public void dropRat(Direction dir) throws GameActionException {
        assertCanDropRat(dir);
        this.robot.dropRobot(dir);
        this.robot.addActionCooldownTurns(GameConstants.THROW_RAT_COOLDOWN);
    }

    public void assertCanCarryRat(MapLocation loc) throws GameActionException {
        assertNotNull(loc);
        // must be senseable and within one square (adjacent)
        assertCanActLocation(loc, 2);
        assertIsActionReady();

        if (!this.robot.getType().isThrowingType()) {
            throw new GameActionException(CANT_DO_THAT, "Unit must be a rat to grab other rats");
        } else if (this.robot.isCarryingRobot()) {
            throw new GameActionException(CANT_DO_THAT, "Already carrying a rat");
        }
        // Must be a rat-type
        if (!this.robot.getType().isBabyRatType()) {
            throw new GameActionException(CANT_DO_THAT, "Only rats can grab other rats!");
        }

        // adjacency
        if (!loc.isAdjacentTo(this.getLocation()) && !this.getLocation().equals(loc)) {
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

        if (targetRobot == this.robot) {
            throw new GameActionException(CANT_DO_THAT, "Robots cannot grab themselves");
        }

        if (targetRobot.getTeam() != this.robot.getTeam() && targetRobot.getLastGrabberId() == this.robot.getID()
                && targetRobot.getTurnsSinceThrownOrDropped() < GameConstants.SAME_ROBOT_CARRY_COOLDOWN_TURNS) {
            throw new GameActionException(CANT_DO_THAT,
                    "Target robot (on the enemy team) was recently carried by this robot");
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
        this.robot.addActionCooldownTurns(GameConstants.THROW_RAT_COOLDOWN);
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
