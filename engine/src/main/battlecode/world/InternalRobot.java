package battlecode.world;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import battlecode.world.CatStateType;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.TrapType;
import battlecode.common.UnitType;

/**
 * The representation of a robot used by the server.
 * Comparable ordering:
 * - tiebreak by creation time (priority to later creation)
 * - tiebreak by robot ID (priority to lower ID)
 */
public class InternalRobot implements Comparable<InternalRobot> {

    private final RobotControllerImpl controller;
    protected final GameWorld gameWorld;

    private int cheeseAmount;
    private UnitType type;

    private final int ID;

    private Team team;
    private MapLocation location;
    private Direction dir;
    private MapLocation diedLocation;
    private int health;

    private long controlBits;
    private int currentBytecodeLimit;
    private int bytecodesUsed;

    private int roundsAlive;
    private int actionCooldownTurns;
    private int movementCooldownTurns;

    private InternalRobot carryingRobot; // robot being carried by this robot, if any
    private InternalRobot grabbedByRobot; // robot that is carrying this robot, if any
    private Direction thrownDir;

    private Queue<Message> incomingMessages;

    // the number of messages this robot/tower has sent this turn
    private int sentMessagesCount;

    private boolean crouching;
    private int chirality; //

    /**
     * Used to avoid recreating the same RobotInfo object over and over.
     */
    private RobotInfo cachedRobotInfo;

    private String indicatorString;

    private ArrayList<Trap> trapsToTrigger;
    private ArrayList<Boolean> enteredTraps;

    private int currentWaypoint;
    private CatStateType catState;
    private MapLocation[] catWaypoints;
    private MapLocation catTargetLoc;
    private int catTurns;
    private InternalRobot catTarget;
    private boolean hasTurned;

    /**
     * Create a new internal representation of a robot
     *
     * @param gw   the world the robot exists in
     * @param type the type of the robot
     * @param loc  the location of the robot
     * @param team the team of the robot
     */
    public InternalRobot(GameWorld gw, int id, Team team, UnitType type, MapLocation loc, Direction dir) {
        this.gameWorld = gw;

        this.ID = id;

        this.team = team;
        this.type = type;

        this.location = loc;
        this.dir = dir;
        this.diedLocation = null;
        this.health = type.health;
        this.incomingMessages = new LinkedList<>();

        this.trapsToTrigger = new ArrayList<>();
        this.enteredTraps = new ArrayList<>();

        this.cheeseAmount = 0;

        this.controlBits = 0;
        this.currentBytecodeLimit = type.bytecodeLimit;
        this.bytecodesUsed = 0;

        this.roundsAlive = 0;
        this.actionCooldownTurns = type.actionCooldown;
        this.movementCooldownTurns = GameConstants.COOLDOWN_LIMIT;

        this.carryingRobot = null;
        this.grabbedByRobot = null;
        this.thrownDir = null;

        this.indicatorString = "";

        this.controller = new RobotControllerImpl(gameWorld, this);

        this.currentWaypoint = 0;
        this.catState = CatStateType.EXPLORE;

        if (this.type.isCatType()) {
            // TODO fix this: are cat index and cat id the same? if not, change this line
            // this.catWaypoints = gw.getGameMap().getCatWaypointsOrdered(id);
            // TODO temporarily we will just find the nearest waypoint

            int minDist = Integer.MAX_VALUE;

            for (int i = 0; i < gw.getGameMap().getNumCats(); i++) {
                MapLocation[] allWaypoints = gw.getGameMap().getCatWaypointsOrdered(i);

                for (MapLocation waypoint : allWaypoints) {
                    if (waypoint != null) {
                        int dist = waypoint.distanceSquaredTo(this.location);

                        if (dist < minDist) {
                            minDist = dist;
                            this.catWaypoints = allWaypoints;
                        }
                    }
                }

                ArrayList<MapLocation> validWaypoints = new ArrayList<>();

                for (MapLocation waypoint : this.catWaypoints) {
                    if (waypoint != null) {
                        validWaypoints.add(waypoint);
                    }
                }

                this.catWaypoints = validWaypoints.toArray(new MapLocation[validWaypoints.size()]);
            }

            this.catTargetLoc = this.catWaypoints[0];
        } else {
            this.catWaypoints = new MapLocation[0];
            this.catTargetLoc = null;
        }

        this.catTurns = 0;

    }

    // ******************************************
    // ****** GETTER METHODS ********************
    // ******************************************

    public RobotControllerImpl getController() {
        return controller;
    }

    public GameWorld getGameWorld() {
        return gameWorld;
    }

    public int getID() {
        return ID;
    }

    public Team getTeam() {
        return team;
    }

    public UnitType getType() {
        return type;
    }

    public MapLocation getLocation() {
        return location;
    }

    public Direction getDirection() {
        return dir;
    }

    public void setDirection(Direction newDir) {
        this.dir = newDir;
    }

    public MapLocation[] getAllPartLocations() {
        return this.getType().getAllLocations(this.location);
    }

    public MapLocation getDiedLocation() {
        return diedLocation;
    }

    public int getHealth() {
        return health;
    }

    public int getCheese() {
        return cheeseAmount;
    }

    public void addCheese(int amount) {
        // TODO: idk if I used this method correctly in my paint -> cheese changes,
        // maybe look through uses of this and check
        if (this.cheeseAmount + amount >= 0) {
            this.cheeseAmount += amount;
        } else {
            amount += this.cheeseAmount;
            this.cheeseAmount = 0;
            this.gameWorld.getTeamInfo().addCheese(getTeam(), amount);
        }
    }

    public long getControlBits() {
        return controlBits;
    }

    public int getBytecodesUsed() {
        return bytecodesUsed;
    }

    public int getRoundsAlive() {
        return roundsAlive;
    }

    public int getActionCooldownTurns() {
        return actionCooldownTurns;
    }

    public int getMovementCooldownTurns() {
        return movementCooldownTurns;
    }

    public InternalRobot getCarryingRobot() {
        return carryingRobot;
    }

    public InternalRobot getGrabbedByRobot() {
        return grabbedByRobot;
    }

    public boolean isBeingThrown() {
        return thrownDir != null;
    }

    public RobotInfo getRobotInfo() {
        // We use the ID of the center of a big robot for sensing related methods
        // so that IDs are consistent regardless of which part of the robot is sensed

        if (cachedRobotInfo != null
                && cachedRobotInfo.ID == ID
                && cachedRobotInfo.team == team
                && cachedRobotInfo.health == health
                && cachedRobotInfo.cheeseAmount == cheeseAmount
                && cachedRobotInfo.location.equals(location)) {
            return cachedRobotInfo;
        }

        this.cachedRobotInfo = new RobotInfo(ID, team, type, health, location, cheeseAmount,
                carryingRobot != null ? carryingRobot.getRobotInfo() : null, crouching);
        return this.cachedRobotInfo;
    }

    // **********************************
    // ****** CHECK METHODS *************
    // **********************************

    /**
     * Returns whether the robot can perform actions, based on cooldowns.
     */
    public boolean canActCooldown() {
        return this.actionCooldownTurns < GameConstants.COOLDOWN_LIMIT;
    }

    /**
     * Returns whether the robot can move, based on cooldowns.
     */
    public boolean canMoveCooldown() {
        return this.movementCooldownTurns < GameConstants.COOLDOWN_LIMIT;
    }

    /**
     * Returns whether the robot is currently carrying another robot.
     */
    public boolean isCarryingRobot() {
        return this.carryingRobot != null;
    }

    /**
     * Returns whether the robot is currently carrying another robot.
     */
    public boolean isGrabbedByRobot() {
        return this.grabbedByRobot != null;
    }

    /**
     * Returns the robot's vision radius squared.
     */
    public int getVisionRadiusSquared() {
        return this.type.getVisionRadiusSquared();
    }

    /**
     * Returns the vision cone's theta
     */
    public int getVisionConeAngle() {
        return this.type.getVisionAngle();
    }

    public boolean hasTurned() {
        return hasTurned;
    }

    public void setHasTurned(boolean hasTurned) {
        this.hasTurned = hasTurned;
    }

    /**
     * Returns whether this robot can sense the given location.
     * 
     * @param toSense the MapLocation to sense
     */
    public boolean canSenseLocation(MapLocation toSense) {
        return this.location.isWithinDistanceSquared(toSense, getVisionRadiusSquared(), this.dir, getVisionConeAngle(),
                this.type.usesTopRightLocationForDistance());
    }

    /**
     * Returns whether this robot can sense a given radius away.
     * 
     * @param radiusSquared the distance squared to sense
     */
    public boolean canSenseRadiusSquared(int radiusSquared) {
        return radiusSquared <= getVisionRadiusSquared();
    }

    /**
     * Returns whether this robot can build a trap on this block
     * 
     * @param build
     * @return boolean: can trap be built here by this robot
     */
    public boolean canBuildTrap(MapLocation build, TrapType trapType) {
        return canSenseLocation(build) && canActCooldown()
                && (this.gameWorld.getTeamInfo().getCheese(this.team) + this.getCheese()) >= trapType.buildCost;
    }

    // ******************************************
    // ****** UPDATE METHODS ********************
    // ******************************************

    /**
     * Sets the indicator string of the robot.
     *
     * @param string the new indicator string of the robot
     */
    public void setIndicatorString(String string) {
        this.indicatorString = string;
    }

    /**
     * Sets the location of the robot.
     * 
     * @param dx # amount to translate in x direction
     * @param dy # amount to translate in y direction
     */
    public void setLocation(int dx, int dy) {
        for (MapLocation partLoc : this.getAllPartLocations()) {
            this.gameWorld.moveRobot(partLoc, partLoc.translate(dx, dy));
        }
        // this.gameWorld.getObjectInfo().moveRobot(this, loc);
        this.location = this.location.translate(dx, dy);
    }

    public boolean canMove(int dx, int dy) {
        for (MapLocation partLoc : this.getAllPartLocations()) {
            MapLocation newLoc = partLoc.translate(dx, dy);
            if (!this.gameWorld.isPassable(newLoc)) {
                return false;
            }
        }
        return true;
    }

    public void setInternalLocationOnly(MapLocation loc) {
        this.location = loc;
    }

    /**
     * Resets the action cooldown.
     */
    public void addActionCooldownTurns(int numActionCooldownToAdd) {
        setActionCooldownTurns(this.actionCooldownTurns + numActionCooldownToAdd);
    }

    /**
     * Resets the movement cooldown.
     */
    public void addMovementCooldownTurns() {
        int movementCooldown = GameConstants.MOVEMENT_COOLDOWN;
        this.setMovementCooldownTurns(this.movementCooldownTurns + movementCooldown);
    }

    /**
     * Sets the action cooldown given the number of turns.
     * 
     * @param newActionTurns the number of action cooldown turns
     */
    public void setActionCooldownTurns(int newActionTurns) {
        this.actionCooldownTurns = newActionTurns;
    }

    /**
     * Sets the movement cooldown given the number of turns.
     * 
     * @param newMovementTurns the number of movement cooldown turns
     */
    public void setMovementCooldownTurns(int newMovementTurns) {
        this.movementCooldownTurns = newMovementTurns;
    }

    /**
     * Adds health to a robot. Input can be negative to subtract health.
     * 
     * @param healthAmount the amount to change health by (can be negative)
     */
    public void addHealth(int healthAmount) {
        health += healthAmount;
        health = Math.min(this.health, this.type.health);
        if (health <= 0) {
            this.gameWorld.destroyRobot(ID, false, true);
        }
    }

    public void addTrapTrigger(Trap t, boolean entered) {
        this.trapsToTrigger.add(t);
        this.enteredTraps.add(entered);
    }

    // *********************************
    // ****** ACTION METHODS *********
    // *********************************

    private int locationToInt(MapLocation loc) {
        return this.gameWorld.locationToIndex(loc);
    }

    /**
     * Method callable by (baby) rat robots to deal small
     * damage to opponent team's (baby) rat robots.
     *
     * @param loc the MapLocation to attempt to bite
     */
    public void bite(MapLocation loc, int cheeseConsumed) {
        if (this.gameWorld.getTeamInfo().getCheese(this.team) + this.getCheese() < cheeseConsumed) {
            throw new RuntimeException("Not enough cheese to bite!");
        }

        if (this.type != UnitType.RAT) {
            throw new RuntimeException("Unit must be a rat to bite!");
        }

        if (!this.canSenseLocation(loc)) {
            return;
        }

        // Must be an immediate neighbor
        int distSq = this.location.distanceSquaredTo(loc);
        if (distSq > 2 || distSq <= 0) {
            return;
        }

        // Determine the direction from this rat to the target tile.
        Direction toTarget = this.location.directionTo(loc);
        if (toTarget == Direction.CENTER) {
            return;
        }

        // If the rat has no facing direction, disallow biting
        if (this.dir == Direction.CENTER) {
            return;
        }

        // Check if there's a robot at that tile
        if (this.gameWorld.getRobot(loc) != null) {
            InternalRobot targetRobot = this.gameWorld.getRobot(loc);

            // Only bite enemy rats and cats
            if (this.team != targetRobot.getTeam()) {
                this.addCheese(-cheeseConsumed);
                targetRobot.addHealth(-GameConstants.RAT_BITE_DAMAGE -
                        (int) Math.ceil(Math.log(cheeseConsumed)));
                this.gameWorld.getMatchMaker().addBiteAction(targetRobot.getID());
            }
        }
    }

    public void scratch(MapLocation loc) {
        if (this.type != UnitType.CAT)
            throw new RuntimeException("Unit must be a cat!");

        // If there's a robot on the tile, deal large damage to it
        if (this.gameWorld.getRobot(loc) != null) {
            InternalRobot robot = this.gameWorld.getRobot(loc);
            if (this.team != robot.getTeam()) {
                robot.addHealth(-GameConstants.CAT_SCRATCH_DAMAGE);
                this.gameWorld.getMatchMaker().addScratchAction(robot.getID());
            }
        }

    }

    public void grabRobot(MapLocation loc) {
        if (!this.type.isThrowingType()) {
            throw new RuntimeException("Unit must be a rat to grab other rats");
        } else if (!loc.isAdjacentTo(this.getLocation())) {
            throw new RuntimeException("A rat can only grab adjacent rats");
        } else if (!canSenseLocation(loc)) { // TODO replace with checking if the target robot is in front of this robot
            throw new RuntimeException("A rat can only grab robots in front of it");
        } else if (this.isCarryingRobot()) {
            throw new RuntimeException("Already carrying a rat");
        } else if (this.isGrabbedByRobot()) { // This should never occur, since grabbed robots are on action cooldown
            throw new RuntimeException("Cannot grab while being carried");
        }

        InternalRobot targetRobot = this.gameWorld.getRobot(loc);

        if (targetRobot != null && targetRobot.getType().isThrowableType()
                && !targetRobot.isBeingThrown()) {
            boolean canGrab = false;

            if (!targetRobot.canSenseLocation(this.location)) { // TODO replace with checking if the enemy robot is
                                                                // facing away from this robot
                canGrab = true; // We can always grab robots facing away from us
            } else if (this.team == this.gameWorld.getRobot(loc).getTeam()) {
                canGrab = true; // We can always grab allied robots
            } else if (this.gameWorld.getRobot(loc).getHealth() + GameConstants.HEALTH_GRAB_THRESHOLD < health) {
                canGrab = true; // We can grab enemy robots with lower strength than us
            }

            if (canGrab) {
                this.carryingRobot = this.gameWorld.getRobot(loc);
                this.carryingRobot.getGrabbed(this); // Notify the grabbed robot that it has been picked up
                this.gameWorld.getMatchMaker().addRatNapAction(this.carryingRobot.getID());
            } else {
                throw new RuntimeException("Cannot grab that robot");
            }
        }
    }

    private void getGrabbed(InternalRobot grabber) {
        this.grabbedByRobot = grabber;
        this.movementCooldownTurns = GameConstants.THROW_DURATION + GameConstants.THROW_STUN_DURATION;
        this.actionCooldownTurns = GameConstants.THROW_DURATION + GameConstants.THROW_STUN_DURATION;
        this.gameWorld.removeRobot(getLocation());
    }

    public void throwRobot(Direction dir) {
        if (!this.type.isThrowingType()) {
            throw new RuntimeException("Unit must be a rat to throw other rats");
        } else if (!this.isCarryingRobot()) {
            throw new RuntimeException("Not carrying a robot to throw");
        }
        if (!this.gameWorld.getGameMap().onTheMap(this.getLocation().add(dir))) {
            throw new RuntimeException("Cannot throw outside of map");
        }

        // Throw the robot
        this.carryingRobot.getThrown(dir);
        this.gameWorld.getMatchMaker().addThrowAction(this.carryingRobot.getID(),
                this.getLocation().add(dir));
        this.carryingRobot = null;
    }

    private void getThrown(Direction dir) {
        this.grabbedByRobot = null;
        this.thrownDir = dir;
        this.setLocation(dir.dx, dir.dy);
    }

    public void hitGround() {
        this.thrownDir = null;
        this.addHealth(-GameConstants.THROW_DAMAGE - GameConstants.THROW_DAMAGE_PER_TURN
                * (this.actionCooldownTurns - GameConstants.THROW_STUN_DURATION) / GameConstants.COOLDOWNS_PER_TURN);
        this.movementCooldownTurns = GameConstants.THROW_STUN_DURATION;
        this.actionCooldownTurns = GameConstants.THROW_STUN_DURATION;
    }

    public void travelFlying() {
        MapLocation newLoc = this.getLocation().add(this.thrownDir);
        if (!this.gameWorld.getGameMap().onTheMap(newLoc) || this.gameWorld.getRobot(newLoc) != null
                || !this.gameWorld.isPassable(newLoc)) {
            this.hitGround();
            return;
        }

        this.setLocation(this.thrownDir.dx, this.thrownDir.dy);

        if (this.actionCooldownTurns <= GameConstants.THROW_STUN_DURATION) {
            this.hitGround();
        }
    }

    /**
     * Attacks another location.
     * The type of attack is based on the robot type (specific methods above)
     * 
     * @param loc the location of the bot
     */
    public void attack(MapLocation loc) {
        switch (this.getType()) {
            case RAT:
                // TODO: bite takes in an amount of cheese consumed. How are competitors going
                // to supply this?
                bite(loc, -1);
                break;
            case CAT:
                scratch(loc);
                break;
            default:
                // TODO
                break;
        }
    }

    public int[] canPounce(MapLocation loc) {
        /*
         * Returns dx, dy of pounce if allowed; otherwise returns null
         */

        // Must be a cat
        if (this.type != UnitType.CAT) {
            throw new RuntimeException("Unit must be a cat to pounce!");
        }

        // Target location must be on map and passable (no walls/dirt) and within max
        // pounce distnace
        boolean isWithinPounceDistance = (this.getLocation()
                    .topRightDistanceSquaredTo(loc) <= GameConstants.CAT_POUNCE_MAX_DISTANCE_SQUARED);
        if (!this.gameWorld.isPassable(loc) || !isWithinPounceDistance) {
            return null;
        }

        // Test all 4 corners of the cat
        MapLocation cornerToTest;
        Direction rotateDir;
        // Check each part of the robot to see if we can pounce so that the part lands
        // on the target location
        MapSymmetry symmetry = this.gameWorld.getGameMap().getSymmetry();

        if (chirality == 0) {
            cornerToTest = this.getLocation();
            rotateDir = Direction.EAST;
        } else {
            switch (symmetry) {
                case VERTICAL:
                    cornerToTest = loc.add(Direction.EAST);
                    rotateDir = Direction.WEST;
                case HORIZONTAL:
                    cornerToTest = loc.add(Direction.SOUTH);
                    rotateDir = Direction.NORTH;
                case ROTATIONAL:
                    cornerToTest = loc.add(Direction.SOUTHEAST);
                    rotateDir = Direction.WEST;
                default:
                    throw new RuntimeException("Invalid symmetry");
            }
        }

        for (int i = 0; i < 4; i += 1) {
            // attempt pounce that matches cornerToTest to target location
            Direction directionFromCornerToTestToCenter = cornerToTest.directionTo(this.getLocation());

            // dx and dy from top left corner
            // assuming getLocation returns the top left corner of the cat
            int dx = directionFromCornerToTestToCenter.dx + (loc.x - this.getLocation().x);
            int dy = directionFromCornerToTestToCenter.dy + (loc.y - this.getLocation().y);

            boolean landingTilesPassable = true;

            // check passability of all landing tiles
            for (MapLocation tile : this.getAllPartLocations()) {
                if (!this.gameWorld.isPassable(tile)) {
                    landingTilesPassable = false;
                }
            }
            if (landingTilesPassable) {
                int[] pounceTraj = { dx, dy };
                return pounceTraj;
            }
            // try another robot part
            cornerToTest.add(rotateDir);
            if (chirality == 0) {
                rotateDir = rotateDir.rotateRight();
                rotateDir = rotateDir.rotateRight();
            } else {
                rotateDir = rotateDir.rotateLeft();
                rotateDir = rotateDir.rotateLeft();
            }
        }
        return null;
    }

    public void pounce(int[] delta) {
        int dx = delta[0];
        int dy = delta[1];
        for (MapLocation partLoc : this.getAllPartLocations()) {
            // shift location by dx, dy
            MapLocation translatedLoc = partLoc.translate(dx, dy);
            InternalRobot crushedRobot = this.gameWorld.getRobot(translatedLoc);
            if (crushedRobot != null) {
                // destroy robot
                gameWorld.destroyRobot(crushedRobot.getID(), false, true);
            }
        }

        // actually translate the cat
        this.setLocation(dx, dy);

    }

    // *********************************
    // ***** COMMUNICATION METHODS *****
    // *********************************

    public int getSentMessagesCount() {
        return sentMessagesCount;
    }

    public Message[] getMessages() {
        return incomingMessages.toArray(new Message[incomingMessages.size()]);
    }

    public Message getFrontMessage() {
        if (incomingMessages.isEmpty())
            return null;
        return incomingMessages.peek();
    }

    public void popMessage() {
        if (!incomingMessages.isEmpty())
            incomingMessages.remove();
    }

    public void addMessage(Message message) {
        incomingMessages.add(message);
    }

    public void sendMessage(InternalRobot robot, Message message) {
        robot.addMessage(message.copy());
    }

    public void incrementMessageCount() {
        this.sentMessagesCount++;
    }

    private void cleanMessages() {
        while (!incomingMessages.isEmpty() && this.getFrontMessage().getRound() <= this.gameWorld.getCurrentRound()
                - GameConstants.MESSAGE_ROUND_DURATION) {
            this.popMessage();
        }
    }

    // ****************************
    // ****** GETTER METHODS ******
    // ****************************

    // *********************************
    // ****** GAMEPLAY METHODS *********
    // *********************************

    // should be called at the beginning of every round
    public void processBeginningOfRound() {
        this.cleanMessages();
        this.indicatorString = "";
        this.diedLocation = null;
    }

    public void processBeginningOfTurn() {
        this.sentMessagesCount = 0;
        if (!this.isGrabbedByRobot()) {
            this.actionCooldownTurns = Math.max(0, this.actionCooldownTurns - GameConstants.COOLDOWNS_PER_TURN);
            this.movementCooldownTurns = Math.max(0, this.movementCooldownTurns - GameConstants.COOLDOWNS_PER_TURN);
            if (this.isBeingThrown()) {
                this.travelFlying(); // This will call hitGround if we hit something or run out of time
            }
        }
        this.currentBytecodeLimit = this.getType().bytecodeLimit;
        this.gameWorld.getMatchMaker().startTurn(this.ID);
    }

    public void processEndOfTurn() {
        // eat cheese if ratking
        if (this.type.isRatKingType()) {
            // ratking starves
            if (this.gameWorld.getTeamInfo().getCheese(team) < GameConstants.RATKING_CHEESE_CONSUMPTION) {
                this.addHealth(-GameConstants.RATKING_HEALTH_LOSS);
            } else {
                this.addCheese(-GameConstants.RATKING_CHEESE_CONSUMPTION);
            }
        }

        // indicator strings!
        if (!indicatorString.equals("")) {
            this.gameWorld.getMatchMaker().addIndicatorString(this.ID, this.indicatorString);
        }

        for (int i = 0; i < trapsToTrigger.size(); i++) {
            // TODO do we really need enteredTraps? I don't see it used anywhere except
            // here, and it's not needed by triggerTrap
            this.gameWorld.triggerTrap(trapsToTrigger.get(i), this/* , enteredTraps.get(i) */);
        }

        this.trapsToTrigger = new ArrayList<>();
        this.enteredTraps = new ArrayList<>();

        this.gameWorld.getMatchMaker().endTurn(this.ID, this.health, this.cheeseAmount, this.movementCooldownTurns,
                this.actionCooldownTurns, this.bytecodesUsed, this.location);
        this.roundsAlive++;

        // cat algo
        // TODO: cat does not care about rats that attack it over other rats, also
        // nothing about feeding has been added
        if (this.type == UnitType.CAT) {
            int[] pounceTraj = null;
            Direction pounceDir = null;

            switch (this.catState) {
                case EXPLORE:
                    MapLocation waypoint = catWaypoints[currentWaypoint];

                    if (this.location.equals(waypoint)) {
                        currentWaypoint = (currentWaypoint + 1) % catWaypoints.length;
                    }

                    this.dir = this.location.directionTo(catWaypoints[currentWaypoint]);

                    // try seeing nearby rats
                    Message squeak = getFrontMessage();
                    InternalRobot[] nearbyRobots = this.gameWorld.getAllRobotsWithinConeRadiusSquared(this.location,
                            this.dir, getVisionConeAngle(), getVisionRadiusSquared(), team);

                    boolean ratVisible = false;
                    InternalRobot rat = null;

                    for (InternalRobot r : nearbyRobots) {
                        if (r.getType().isRatType()) {
                            ratVisible = true;
                            rat = r;
                        }
                    }

                    if (ratVisible) {
                        // upon seeing a rat immediately go to attack it, otherwise chase then search
                        this.catTargetLoc = rat.getLocation();
                        this.catState = CatStateType.ATTACK;
                        this.catTarget = rat;
                    } else if (squeak != null) {
                        this.catTargetLoc = squeak.getSource();
                        this.catState = CatStateType.CHASE;
                    } else {
                        this.catTargetLoc = waypoint;
                    }

                    Direction toWaypoint = this.location.directionTo(this.catTargetLoc);
                    System.out.println("DEBUGGING: " + toWaypoint);
                    this.dir = this.location.directionTo(this.catTargetLoc);

                    if (this.movementCooldownTurns == 0 && canMove(toWaypoint.getDeltaX(), toWaypoint.getDeltaY())) {
                        setLocation(toWaypoint.getDeltaX(), toWaypoint.getDeltaY());
                    } else {
                        for (MapLocation partLoc : this.getAllPartLocations()) {
                            MapLocation nextLoc = partLoc.add(toWaypoint);

                            if (this.actionCooldownTurns == 0 && (this.gameWorld.getDirt(nextLoc))) {
                                this.gameWorld.setDirt(nextLoc, false);
                                this.addActionCooldownTurns(GameConstants.CAT_DIG_COOLDOWN);
                            }
                        }
                    }
                    break;

                case CHASE:
                    Direction toTarget = this.location.directionTo(this.catTargetLoc);
                    this.dir = toTarget;

                    if (this.location.equals(this.catTargetLoc)) {
                        this.catState = CatStateType.SEARCH;
                    }

                    // pounce towards target if possible
                    pounceTraj = canPounce(this.catTargetLoc);

                    if (canActCooldown() && pounceTraj != null) {
                        this.pounce(pounceTraj);
                    } else if (canMoveCooldown() && canMove(this.dir.getDeltaX(), this.dir.getDeltaY())) {
                        setLocation(this.dir.getDeltaX(), this.dir.getDeltaY());
                    } else {
                        for (MapLocation partLoc : this.getAllPartLocations()) {
                            MapLocation nextLoc = partLoc.add(this.dir);

                            if (this.actionCooldownTurns == 0 && (this.gameWorld.getDirt(nextLoc))) {
                                this.gameWorld.setDirt(nextLoc, false);
                                this.addActionCooldownTurns(GameConstants.CAT_DIG_COOLDOWN);
                            }
                        }
                    }
                    break;

                case SEARCH:
                    if (this.catTurns >= 4) {
                        this.catTurns = 0;
                        this.catState = CatStateType.EXPLORE;
                        break;
                    }

                    this.dir = this.dir.rotateLeft().rotateLeft();

                    nearbyRobots = this.gameWorld.getAllRobotsWithinConeRadiusSquared(this.location, this.dir,
                            getVisionConeAngle(), getVisionRadiusSquared(), team);

                    ratVisible = false;
                    rat = null;

                    for (InternalRobot r : nearbyRobots) {
                        if (r.getType().isRatType()) {
                            ratVisible = true;
                            rat = r;
                        }
                    }

                    if (ratVisible) {
                        this.catTargetLoc = rat.getLocation();
                        this.catTarget = rat;
                        this.catState = CatStateType.ATTACK;
                    }

                    this.catTurns += 1;
                    break;

                case ATTACK:
                    // step 1: try to find the rat it was attacking, if cannot find it go back to
                    // explore
                    nearbyRobots = this.gameWorld.getAllRobotsWithinConeRadiusSquared(this.location, this.dir,
                            getVisionConeAngle(), getVisionRadiusSquared(), team);

                    ratVisible = false;

                    for (InternalRobot r : nearbyRobots) {
                        if (r.equals(this.catTarget)) {
                            ratVisible = true;
                            this.catTargetLoc = this.catTarget.getLocation();
                        }
                    }

                    if (!ratVisible) {
                        this.catState = CatStateType.EXPLORE;
                        break;
                    }

                    // step 2: try to attack it and move towards it

                    if (canActCooldown()) {
                        attack(this.catTarget.getLocation());
                    }

                    this.dir = this.location.directionTo(this.catTargetLoc);

                    // pounce towards target if possible
                    pounceTraj = canPounce(this.catTargetLoc);

                    if (canActCooldown() && pounceTraj != null) {
                        this.pounce(pounceTraj);
                    } else if (canMoveCooldown() && canMove(this.dir.getDeltaX(), this.dir.getDeltaY())) {
                        setLocation(this.dir.getDeltaX(), this.dir.getDeltaY());
                    } else {
                        for (MapLocation partLoc : this.getAllPartLocations()) {
                            MapLocation nextLoc = partLoc.add(this.dir);

                            if (this.controller.canRemoveDirt(nextLoc)) {
                                this.controller.removeDirt(nextLoc);
                                this.addActionCooldownTurns(GameConstants.CAT_DIG_COOLDOWN);
                            }
                        }
                    }
                    break;
            }
        }
    }

    // *********************************
    // ****** BYTECODE METHODS *********
    // *********************************

    public boolean canExecuteCode() {
        return true;
    }

    public void setBytecodesUsed(int numBytecodes) {
        this.bytecodesUsed = numBytecodes;
    }

    public int getBytecodeLimit() {
        return canExecuteCode() ? this.currentBytecodeLimit : 0;
    }

    // *********************************
    // ****** VARIOUS METHODS **********
    // *********************************

    public void die_exception() {
        this.gameWorld.destroyRobot(getID(), true, false);
    }

    // *****************************************
    // ****** MISC. METHODS ********************
    // *****************************************

    @Override
    public boolean equals(Object o) {
        return o != null && (o instanceof InternalRobot)
                && ((InternalRobot) o).getID() == ID;
    }

    @Override
    public int hashCode() {
        return ID;
    }

    @Override
    public String toString() {
        return String.format("%s#%d", getTeam(), getID());
    }

    @Override
    public int compareTo(InternalRobot o) {
        if (this.roundsAlive != o.roundsAlive)
            return this.roundsAlive - o.roundsAlive;
        return this.ID - o.ID;
    }
}
