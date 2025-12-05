package battlecode.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import battlecode.common.CatStateType;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
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

    static boolean isTracing = false;
    static int smallestDistance = 10000000;
    static MapLocation closestLocation = null;
    static Direction tracingDir= null;

    static MapLocation prevDest = null;
    static HashSet<MapLocation> line = null;
    static int obstacleStartDist = 0;

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
        this.currentBytecodeLimit = type.isRobotType() ? GameConstants.ROBOT_BYTECODE_LIMIT
                : GameConstants.TOWER_BYTECODE_LIMIT;
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

        this.catWaypoints = gw.getGameMap().getCatWaypointsOrdered(id);

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

    // public boolean isCenterRobot(){
    // return this.offsetToCenter == Direction.CENTER;
    // }

    // public InternalRobot getCenterRobot(){
    // MapLocation centerLocation = this.location.add(offsetToCenter);
    // return this.gameWorld.getRobot(centerLocation);
    // }

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
        return this.type.getVisionRadius();
    }

    /**
     * Returns the vision cone's theta
     */
    public int getVisionConeAngle() {
        return this.type.getVisionAngle();
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

    public void setInternalLocationOnly(MapLocation loc) {
        this.location = loc;
    }

    /**
     * Upgrades the level of a tower.
     * 
     * @param robot the tower to be upgraded
     */
    public void upgradeTower(UnitType newType) {
        int damage = this.type.health - getHealth();
        this.type = newType;
        this.health = newType.health - damage;
    }

    /**
     * Resets the action cooldown.
     */
    public void addActionCooldownTurns(int numActionCooldownToAdd) {
        int paintPercentage = (int) Math.round(this.paintAmount * 100.0 / this.type.paintCapacity);
        /*
         * TODO this is paint depletion logic and can probably be removed
         * if (paintPercentage < GameConstants.INCREASED_COOLDOWN_THRESHOLD &&
         * type.isRobotType()) {
         * numActionCooldownToAdd += (int) Math.round(numActionCooldownToAdd
         * (GameConstants.INCREASED_COOLDOWN_INTERCEPT +
         * GameConstants.INCREASED_COOLDOWN_SLOPE * paintPercentage)
         * / 100.0);
         * }
         */
        setActionCooldownTurns(this.actionCooldownTurns + numActionCooldownToAdd);
    }

    /**
     * Resets the movement cooldown.
     */
    public void addMovementCooldownTurns() {
        int movementCooldown = GameConstants.MOVEMENT_COOLDOWN;
        int paintPercentage = (int) Math.round(this.paintAmount * 100.0 / this.type.paintCapacity);
        /*
         * TODO this is paint depletion logic and can probably be removed
         * if (paintPercentage < GameConstants.INCREASED_COOLDOWN_THRESHOLD &&
         * type.isRobotType()) {
         * movementCooldown += (int) Math.round(movementCooldown
         * (GameConstants.INCREASED_COOLDOWN_INTERCEPT +
         * GameConstants.INCREASED_COOLDOWN_SLOPE * paintPercentage)
         * / 100.0);
         * }
         */
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
        InternalRobot centerRobot = this.getCenterRobot();
        centerRobot.health += healthAmount;
        centerRobot.health = Math.min(this.health, this.type.health);
        if (centerRobot.health <= 0) {
            this.gameWorld.destroyRobot(centerRobot.ID, false, true);
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
    public void bite(MapLocation loc) {
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

            // Only bite enemy rats
            if (this.team != targetRobot.getTeam() && targetRobot.getType() == UnitType.RAT) {
                targetRobot.addHealth(-GameConstants.RAT_BITE_DAMAGE);
                this.gameWorld.getMatchMaker().addAttackAction(targetRobot.getID());
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
                this.gameWorld.getMatchMaker().addAttackAction(robot.getID());
            }
        }

    }

    public void grabRobot(MapLocation loc) {
        if (!this.type.isThrowingType()) {
            throw new RuntimeException("Unit must be a rat to grab other rats");
        } else if (!loc.isAdjacentTo(this.getLocation())) {
            throw new RuntimeException("Can only grab adjacent robots");
        } else if (false) { // TODO
            throw new RuntimeException("Can only grab robots in front of us");
        } else if (this.isCarryingRobot()) {
            throw new RuntimeException("Already carrying a robot");
        } else if (this.isGrabbedByRobot()) { // This should never occur, since grabbed robots are on action cooldown
            throw new RuntimeException("Cannot grab while being carried");
        }

        if (this.gameWorld.getRobot(loc) != null && this.gameWorld.getRobot(loc).getType().isThrowableType()
                && !this.gameWorld.getRobot(loc).isBeingThrown()) {
            boolean canGrab = false;
            if (false) { // TODO replace with checking if the enemy robot is facing away from us
                canGrab = true; // We can always grab robots facing away from us
            } else if (this.team == this.gameWorld.getRobot(loc).getTeam()) {
                canGrab = true; // We can always grab allied robots
            } else if (this.gameWorld.getRobot(loc).getHealth() + GameConstants.HEALTH_GRAB_THRESHOLD < health) {
                canGrab = true; // We can grab enemy robots with lower strength than us
            }

            if (canGrab) {
                this.carryingRobot = this.gameWorld.getRobot(loc);
                this.carryingRobot.getGrabbed(this); // Notify the grabbed robot that it has been picked up
                this.gameWorld.getMatchMaker().addGrabAction(this.carryingRobot.getID());
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
                locationToInt(this.getLocation().add(dir)));
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
                bite(loc);
                break;
            case CAT:
                scratch(loc);
                break;
            default:
                // TODO
                break;
        }
    }

    public void canPounce(MapLocation loc){
        //check if cat can pounce to this location; tries using all 4 parts of the cat landing on loc
        
    }

    public void pounce(MapLocation loc) {
        // Must be a cat
        if (this.type != UnitType.CAT) {
            throw new RuntimeException("Unit must be a cat to pounce!");
        }
        // disallow pounce to current tile or extremely far tiles
        int distSq = this.location.distanceSquaredTo(loc);
        if (distSq <= 0) {
            return;
        }
        if (distSq > GameConstants.CAT_POUNCE_MAX_DISTANCE_SQUARED) {
            return;
        }
        // Landing tile must be on map and passable (no walls/dirt)

        if (!this.gameWorld.isPassable(loc)) {
            return;
        }
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
        if (this.type.paintPerTurn != 0)
            addPaint(this.type.paintPerTurn);
        if (this.type.moneyPerTurn != 0)
            this.gameWorld.getTeamInfo().addMoney(this.team, this.type.moneyPerTurn);

        // Add upgrade action for initially upgraded starting towers
        if (this.type.isTowerType() && this.gameWorld.getCurrentRound() == 1 && this.type.level == 2) {
            this.getGameWorld().getMatchMaker().addUpgradeAction(getID(), getHealth(),
                    getType().health, getPaint(), getType().paintCapacity);
        }
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
        this.currentBytecodeLimit = this.type.isRobotType() ? GameConstants.ROBOT_BYTECODE_LIMIT
                : GameConstants.TOWER_BYTECODE_LIMIT;
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
            this.gameWorld.triggerTrap(trapsToTrigger.get(i), this, enteredTraps.get(i));
        }
        this.trapsToTrigger = new ArrayList<>();
        this.enteredTraps = new ArrayList<>();

        this.gameWorld.getMatchMaker().endTurn(this.ID, this.health, this.cheeseAmount, this.movementCooldownTurns,
                this.actionCooldownTurns, this.bytecodesUsed, this.location);
        this.roundsAlive++;

        // cat algo
        if (this.type == UnitType.CAT) {
            switch (this.catState) {
                case EXPLORE:
                    MapLocation waypoint = catWaypoints[currentWaypoint];
                    Direction toWaypoint = this.location.directionTo(waypoint);
                    if (this.location.equals(waypoint)) {
                        currentWaypoint = (currentWaypoint + 1) % catWaypoints.length;
                    } else {
                        // move to waypoint
                        
                    }
                    break;
                case CHASE:
                    // TODO
                    break;
                case SEARCH:
                    // TODO
                    break;
                case ATTACK:
                    // TODO
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

    public static void bug2(RobotController rc, MapLocation target) throws GameActionException{
        
        if(!target.equals(prevDest)) {
            prevDest = target;
            line = createLine(rc.getLocation(), target);
        }

        for(MapLocation loc : line) {
            rc.setIndicatorDot(loc, 255, 0, 0);
        }

        if(!isTracing) {
            Direction dir = rc.getLocation().directionTo(target);
            rc.setIndicatorDot(rc.getLocation().add(dir), 255, 0, 0);

            if(rc.canMove(dir)){
                rc.move(dir);
            } else {
                isTracing = true;
                obstacleStartDist = rc.getLocation().distanceSquaredTo(target);
                tracingDir = dir;
            }
        } else {
            if(line.contains(rc.getLocation()) && rc.getLocation().distanceSquaredTo(target) < obstacleStartDist) {
                isTracing = false;
            }

            for(int i = 0; i < 9; i++){
                if(rc.canMove(tracingDir)){
                    rc.move(tracingDir);
                    tracingDir = tracingDir.rotateRight();
                    tracingDir = tracingDir.rotateRight();
                    break;
                } else {
                    tracingDir = tracingDir.rotateLeft();
                }
            }
        }
    }

    // Bresenham's line algorithm for bug2
    public static HashSet<MapLocation> createLine(MapLocation a, MapLocation b) {
        HashSet<MapLocation> locs = new HashSet<>();
        int x = a.x, y = a.y;
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        int sx = (int) Math.signum(dx);
        int sy = (int) Math.signum(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        int d = Math.max(dx,dy);
        int r = d/2;
        if (dx > dy) {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                x += sx;
                r += dy;
                if (r >= dx) {
                    locs.add(new MapLocation(x, y));
                    y += sy;
                    r -= dx;
                }
            }
        }
        else {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                y += sy;
                r += dx;
                if (r >= dy) {
                    locs.add(new MapLocation(x, y));
                    x += sx;
                    r -= dy;
                }
            }
        }
        locs.add(new MapLocation(x, y));
        return locs;
    }
}
