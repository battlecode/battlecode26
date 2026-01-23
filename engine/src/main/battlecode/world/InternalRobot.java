package battlecode.world;

import java.util.*;

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
    private int turningCooldownTurns;

    private int turnsSinceThrownOrDropped;
    private int lastGrabberId;
    private InternalRobot robotBeingCarried; // robot being carried by this robot, if any
    private InternalRobot grabbedByRobot; // robot that is carrying this robot, if any
    private Direction thrownDir;
    private int remainingThrowDuration; // how much longer robot should be thrown for
    private int remainingCarriedDuration; // Number of turns before we wriggle free from enemy robot

    private Queue<Message> incomingMessages;

    // the number of messages this robot/tower has sent this turn
    private int sentMessagesCount;

    public static Random rand = new Random(1092);

    private int chirality;
    private int sleepTimeRemaining;

    /**
     * Used to avoid recreating the same RobotInfo object over and over.
     */
    private RobotInfo cachedRobotInfo;

    private String indicatorString;

    private int currentWaypoint;
    private int previousWaypoint;
    private CatStateType catState;
    private MapLocation[] catWaypoints;
    private MapLocation catTargetLoc;
    private int catTurns;
    private RobotInfo catTarget;
    private int catTurnsStuck;

    /**
     * Create a new internal representation of a robot
     *
     * @param gw   the world the robot exists in
     * @param type the type of the robot
     * @param loc  the location of the robot
     * @param team the team of the robot
     */
    public InternalRobot(GameWorld gw, int id, Team team, UnitType type, MapLocation loc, Direction dir,
            int chirality) {
        this.gameWorld = gw;

        this.ID = id;

        this.team = team;
        this.type = type;

        this.location = loc;
        this.dir = dir;
        this.diedLocation = null;
        this.health = type.health;
        this.incomingMessages = new LinkedList<>();
        this.cheeseAmount = 0;

        this.controlBits = 0;
        this.currentBytecodeLimit = type.bytecodeLimit;
        this.bytecodesUsed = 0;

        this.roundsAlive = 0;
        this.actionCooldownTurns = GameConstants.COOLDOWN_LIMIT;
        this.movementCooldownTurns = GameConstants.COOLDOWN_LIMIT;
        this.turningCooldownTurns = GameConstants.COOLDOWN_LIMIT;

        this.turnsSinceThrownOrDropped = GameConstants.GAME_MAX_NUMBER_OF_ROUNDS; // not recently thrown or dropped
        this.lastGrabberId = -1;
        this.robotBeingCarried = null;
        this.grabbedByRobot = null;
        this.thrownDir = null;
        this.remainingThrowDuration = 0;
        this.remainingCarriedDuration = 0;

        this.indicatorString = "";

        this.controller = new RobotControllerImpl(gameWorld, this);

        this.currentWaypoint = 0;
        this.previousWaypoint = 0;
        this.catState = CatStateType.EXPLORE;
        this.sleepTimeRemaining = 0;
        this.chirality = chirality;

        if (this.type.isCatType()) {

            // set waypoints
            int[] waypointIndexLocations = gw.getGameMap().getCatWaypointsByID(this.ID);
            catWaypoints = new MapLocation[waypointIndexLocations.length];
            for (int i = 0; i < waypointIndexLocations.length; i++) {
                catWaypoints[i] = this.gameWorld.indexToLocation(waypointIndexLocations[i]);
            }

            this.catTargetLoc = this.catWaypoints[0];

        } else {
            this.catWaypoints = new MapLocation[0];
            this.catTargetLoc = null;
        }

        this.catTurns = 0;
        this.catTurnsStuck = 0;
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

    public int getChirality() {
        return chirality;
    }

    public void setDirection(Direction newDir) {
        this.dir = newDir;
    }

    public MapLocation[] getAllRatLocations() {
        // return part location in order based on chirality
        MapLocation[] locs = new MapLocation[this.type.size * this.type.size];
        int c = 0;

        ArrayList<Integer> x_values = new ArrayList<>();
        ArrayList<Integer> y_values = new ArrayList<>();

        for (int i = -(this.type.size - 1) / 2; i <= this.type.size / 2; i++) {
            x_values.add(i);
        }

        for (int j = -(this.type.size - 1) / 2; j <= this.type.size / 2; j++) {
            y_values.add(j);
        }

        if (chirality == 1) {
            MapSymmetry symmetry = this.gameWorld.getGameMap().getSymmetry();
            switch (symmetry) {
                case HORIZONTAL:
                    Collections.reverse(y_values);
                    break;
                case VERTICAL:
                    Collections.reverse(x_values);
                    break;
                case ROTATIONAL:
                    Collections.reverse(x_values);
                    Collections.reverse(y_values);
                    break;
            }
        }

        for (int i : x_values) {
            for (int j : y_values) {
                locs[c] = new MapLocation(this.location.x + i, this.location.y - j);
                c += 1;
            }
        }
        return locs;
    }

    public MapLocation[] getAllPartLocations() {
        if (this.type.isCatType())
            return getAllCatLocationsByChirality();
        else
            return this.getAllRatLocations();
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
        if (this.getType() == UnitType.RAT_KING) {
            this.gameWorld.getTeamInfo().addCheese(getTeam(), amount);
            return;
        }

        // for rats, first add/remove from local stash
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

    public int getTurningCooldownTurns() {
        return turningCooldownTurns;
    }

    public InternalRobot getRobotBeingCarried() {
        return robotBeingCarried;
    }

    public void clearCarryingRobot() {
        this.robotBeingCarried = null;
    }

    public int getRemainingCarriedDuration() {
        return remainingCarriedDuration;
    }

    public InternalRobot getGrabbedByRobot() {
        return grabbedByRobot;
    }

    public void clearGrabbedByRobot() {
        this.grabbedByRobot = null;
    }

    public boolean isBeingThrown() {
        return thrownDir != null;
    }

    public int getTurnsSinceThrownOrDropped() {
        return turnsSinceThrownOrDropped;
    }

    public int getLastGrabberId() {
        return lastGrabberId;
    }

    public RobotInfo getRobotInfo() {
        // We use the ID of the center of a big robot for sensing related methods
        // so that IDs are consistent regardless of which part of the robot is sensed

        if (cachedRobotInfo != null
                && cachedRobotInfo.ID == ID
                && cachedRobotInfo.team == team
                && cachedRobotInfo.type == type
                && cachedRobotInfo.health == health
                && cachedRobotInfo.cheeseAmount == cheeseAmount
                && cachedRobotInfo.chirality == chirality
                && cachedRobotInfo.direction == dir
                && ((cachedRobotInfo.carryingRobot == null && robotBeingCarried == null)
                        || (robotBeingCarried != null
                                && cachedRobotInfo.carryingRobot == robotBeingCarried.getRobotInfo()))
                && cachedRobotInfo.location.equals(location)) {
            return cachedRobotInfo;
        }

        this.cachedRobotInfo = new RobotInfo(ID, team, type, health, location, dir, chirality, cheeseAmount,
                robotBeingCarried != null ? robotBeingCarried.getRobotInfo() : null);
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
     * Returns whether the robot can turn, based on cooldowns.
     */
    public boolean canTurnCooldown() {
        return this.turningCooldownTurns < GameConstants.COOLDOWN_LIMIT;
    }

    /**
     * Returns whether the robot is currently carrying another robot.
     */
    public boolean isCarryingRobot() {
        return this.robotBeingCarried != null;
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

    /**
     * Returns whether this robot can sense the given location.
     * 
     * @param toSense the MapLocation to sense
     */
    public boolean canSenseLocation(MapLocation toSense) {
        return this.location.isWithinDistanceSquared(toSense, getVisionRadiusSquared(), this.dir, getVisionConeAngle(),
                this.type.usesBottomLeftLocationForDistance());
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
     * Sets the location of the robot by translating it.
     * 
     * @param dx # amount to translate in x direction
     * @param dy # amount to translate in y direction
     */
    public void translateLocation(int dx, int dy) {
        MapLocation[] beforeLocs = this.getAllPartLocations();
        for (MapLocation partLoc : beforeLocs) {
            this.gameWorld.removeRobot(partLoc);
        }

        for (MapLocation partLoc : beforeLocs) {
            this.gameWorld.addRobot(partLoc.translate(dx, dy), this);
        }

        // this.gameWorld.getObjectInfo().moveRobot(this, loc);
        this.location = this.location.translate(dx, dy);

        if (!this.type.isCatType() && this.isCarryingRobot()) {
            this.robotBeingCarried.setInternalLocationOnly(this.location);
        }
    }

    public void setInternalLocationOnly(MapLocation loc) {
        this.location = loc;
    }

    public void becomeRatKing(int health) {
        this.type = UnitType.RAT_KING;
        this.health = health;
    }

    /**
     * Resets the action cooldown.
     */
    public void addActionCooldownTurns(int numActionCooldownToAdd) {
        int cooldownUp = numActionCooldownToAdd
                * (int) (this.robotBeingCarried != null ? GameConstants.CARRY_COOLDOWN_MULTIPLIER : 1);

        if (getType() == UnitType.BABY_RAT) {
            cooldownUp = (int) (((double) cooldownUp)
                    * (1.0 + this.cheeseAmount * GameConstants.CHEESE_COOLDOWN_PENALTY));
        }
        setActionCooldownTurns(this.actionCooldownTurns + cooldownUp);
    }

    /**
     * Resets the movement cooldown.
     */
    public void addMovementCooldownTurns(Direction d) {
        int movementCooldown = this.getType().movementCooldown;

        if (getType() == UnitType.BABY_RAT && this.dir != d) {
            movementCooldown = GameConstants.MOVE_STRAFE_COOLDOWN;
        }

        movementCooldown *= (int) (this.robotBeingCarried != null ? GameConstants.CARRY_COOLDOWN_MULTIPLIER : 1);

        if (getType() == UnitType.BABY_RAT) {
            movementCooldown = (int) (((double) movementCooldown)
                    * (1.0 + this.cheeseAmount * GameConstants.CHEESE_COOLDOWN_PENALTY));
        }

        this.setMovementCooldownTurns(this.movementCooldownTurns + movementCooldown);
    }

    /**
     * Resets the turning cooldown.
     */
    public void addTurningCooldownTurns() {
        int turningCooldown = GameConstants.TURNING_COOLDOWN
                * (int) (this.robotBeingCarried != null ? GameConstants.CARRY_COOLDOWN_MULTIPLIER : 1);
        this.setTurningCooldownTurns(this.turningCooldownTurns + turningCooldown);
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
     * Sets the turning cooldown given the number of turns.
     * 
     * @param newMovementTurns the number of turning cooldown turns
     */
    public void setTurningCooldownTurns(int newTurningTurns) {
        this.turningCooldownTurns = newTurningTurns;
    }

    /**
     * Adds health to a robot. Input can be negative to subtract health.
     * 
     * @param healthAmount the amount to change health by (can be negative)
     */
    public void addHealth(int healthAmount) {
        this.health += healthAmount;

        if (healthAmount < 0 && this.getType() != UnitType.CAT)
            this.gameWorld.getTeamInfo().addDamageSuffered(this.team, -healthAmount);

        this.health = Math.min(this.health, this.type.health);
        if (this.type == UnitType.CAT) {
            this.gameWorld.updateCatHealth(this.ID, health);
        }
        if (this.health <= 0) {
            this.gameWorld.destroyRobot(this.getID(), false, true);
        }
    }

    // *********************************
    // ****** ACTION METHODS *********
    // *********************************

    // private int locationToInt(MapLocation loc) {
    // return this.gameWorld.locationToIndex(loc);
    // }

    /**
     * Method callable by (baby) rat robots to deal small
     * damage to opponent team's (baby) rat robots.
     *
     * @param loc the MapLocation to attempt to bite
     */
    public void bite(MapLocation loc, int cheeseConsumed) {
        // Must be an immediate neighbor
        int distSq = this.location.distanceSquaredTo(loc);

        if (distSq == 0 || distSq > (this.type == UnitType.RAT_KING
                ? GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED
                : GameConstants.ATTACK_DISTANCE_SQUARED)) {
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
                int damage = GameConstants.RAT_BITE_DAMAGE;

                if (cheeseConsumed > 0) {
                    this.addCheese(-cheeseConsumed);
                    damage += (int) Math.ceil(Math.sqrt(cheeseConsumed));
                }

                this.gameWorld.getMatchMaker().addBiteAction(targetRobot.ID);

                if (targetRobot.getType() == UnitType.CAT) {
                    this.gameWorld.getTeamInfo().addDamageToCats(team, Math.min(damage, targetRobot.getHealth()));
                }

                targetRobot.addHealth(-damage);

                if (targetRobot.getType() != UnitType.CAT) {
                    this.gameWorld.backstab(this.team);
                }
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
                this.gameWorld.getMatchMaker().addScratchAction(this.getGameWorld().locationToIndex(loc));
            }
        }
    }

    public void grabRobot(MapLocation loc) {
        this.robotBeingCarried = this.gameWorld.getRobot(loc);
        this.robotBeingCarried.getGrabbed(this); // Notify the grabbed robot that it has been picked up
        this.gameWorld.getMatchMaker().addRatNapAction(this.robotBeingCarried.getID());

        if (this.robotBeingCarried.getTeam() != this.getTeam()) {
            this.gameWorld.backstab(this.getTeam());
        }
    }

    public void dropRobot(Direction dir) {
        MapLocation dropLoc = this.getLocation().add(dir);
        this.robotBeingCarried.getDropped(dropLoc);
        this.robotBeingCarried = null;
    }

    private void swapGrabber() {
        if (!this.isGrabbedByRobot()) {
            throw new RuntimeException("Must be grabbed to swap");
        }

        InternalRobot grabber = this.getGrabbedByRobot();
        MapLocation dropLoc = grabber.getLocation();

        this.robotBeingCarried = grabber;
        grabber.grabbedByRobot = this;

        this.grabbedByRobot = null;
        grabber.robotBeingCarried = null;

        grabber.setInternalLocationOnly(dropLoc);
        this.setInternalLocationOnly(dropLoc);

        this.gameWorld.removeRobot(dropLoc);
        this.gameWorld.addRobot(dropLoc, this);

        this.gameWorld.getMatchMaker().addRatNapAction(this.ID); // expand this rat
        this.gameWorld.getMatchMaker().addRatNapAction(grabber.ID); // shrink carrier rat

        grabber.remainingCarriedDuration = GameConstants.MAX_CARRY_DURATION;
    }

    private void getGrabbed(InternalRobot grabber) {
        this.turnsSinceThrownOrDropped = 0;
        this.grabbedByRobot = grabber;
        this.lastGrabberId = grabber.getID();
        this.gameWorld.removeRobot(getLocation());

        if (this.isCarryingRobot()) { // If we were carrying a robot, drop it
            this.robotBeingCarried.getDropped(getLocation());
            this.robotBeingCarried = null;
        }

        this.setInternalLocationOnly(grabber.getLocation());

        this.remainingCarriedDuration = GameConstants.MAX_CARRY_DURATION;

    }

    public void throwRobot() {
        this.gameWorld.getMatchMaker().endTurn(this.ID, this.health, this.cheeseAmount, this.movementCooldownTurns,
                this.actionCooldownTurns, this.turningCooldownTurns, this.bytecodesUsed, this.location, this.dir,
                this.gameWorld.isCooperation());
        this.robotBeingCarried.getThrown(this.dir);
        this.gameWorld.getMatchMaker().endTurn(this.robotBeingCarried.ID, this.robotBeingCarried.health,
                this.robotBeingCarried.cheeseAmount, this.robotBeingCarried.movementCooldownTurns,
                this.robotBeingCarried.actionCooldownTurns, this.robotBeingCarried.turningCooldownTurns,
                this.robotBeingCarried.bytecodesUsed, this.robotBeingCarried.location, this.robotBeingCarried.dir,
                this.gameWorld.isCooperation());
        this.gameWorld.addHasTraveledRobot(this.robotBeingCarried.getID());
        this.gameWorld.getMatchMaker().addThrowAction(this.robotBeingCarried.getID(),
                this.getLocation().add(this.dir));
        this.robotBeingCarried = null;
    }

    private void getThrown(Direction dir) {
        this.turnsSinceThrownOrDropped = 0;
        this.grabbedByRobot = null;
        this.remainingCarriedDuration = 0;
        this.thrownDir = dir;
        this.remainingThrowDuration = 4;

        this.setInternalLocationOnly(this.getLocation());

        this.travelFlying(false);
        this.travelFlying(true);
    }

    public void getDropped(MapLocation loc) {
        if (!this.gameWorld.getGameMap().onTheMap(loc)) {
            throw new RuntimeException("Cannot drop outside of map");
        } else if (this.gameWorld.getRobot(loc) != null) {
            throw new RuntimeException("Cannot drop into occupied space");
        } else if (!this.gameWorld.isPassable(loc)) {
            throw new RuntimeException("Cannot drop into impassable terrain");
        }

        this.turnsSinceThrownOrDropped = 0;
        this.grabbedByRobot = null;
        this.remainingCarriedDuration = 0;
        this.setInternalLocationOnly(loc);

        this.gameWorld.getMatchMaker().addRatNapAction(this.getID());

        if (this.getHealth() > 0) {
            this.gameWorld.addRobot(this.getLocation(), this);
            this.controller.processTrapsAtLocation(this.location);
        } else
            this.gameWorld.destroyRobot(this.getID());
    }

    public void hitGround() {
        this.turnsSinceThrownOrDropped = 0;
        this.thrownDir = null;
        this.remainingThrowDuration = 0;

        int damage = GameConstants.THROW_DAMAGE;
        this.addHealth(-damage);

        this.gameWorld.getMatchMaker().addDamageAction(this.ID, damage);
        this.gameWorld.getMatchMaker().addRatNapAction(this.getID());
        this.gameWorld.removeFlyingRobot(this.location);

        if (this.health > 0) {
            this.gameWorld.addRobot(this.location, this);
            this.controller.processTrapsAtLocation(this.location);
        } else {
            this.gameWorld.destroyRobot(this.getID());
        }

        setMovementCooldownTurns(this.movementCooldownTurns + GameConstants.HIT_GROUND_COOLDOWN);
        setActionCooldownTurns(this.actionCooldownTurns + GameConstants.HIT_GROUND_COOLDOWN);
        setTurningCooldownTurns(this.turningCooldownTurns + GameConstants.HIT_GROUND_COOLDOWN);

        this.gameWorld.getMatchMaker().addStunAction(this.ID, GameConstants.HIT_GROUND_COOLDOWN);
    }

    public void hitTarget(boolean isSecondMove) {
        int damage = GameConstants.THROW_DAMAGE
                + GameConstants.THROW_DAMAGE_PER_TILE
                        * (GameConstants.TILES_FLOWN_PER_TURN * this.remainingThrowDuration + (isSecondMove ? 0 : 1));

        if (this.gameWorld.getRobot(this.getLocation().add(this.thrownDir)) != null) {
            InternalRobot robot = this.gameWorld.getRobot(this.getLocation().add(this.thrownDir));
            robot.addHealth(-damage);
        }
        else if (this.gameWorld.getFlyingRobot(this.getLocation().add(this.thrownDir)) != null){
            InternalRobot robot = this.gameWorld.getFlyingRobot(this.getLocation().add(this.thrownDir));
            robot.remainingThrowDuration = 1; // force other robot to drop to ground as well on next turn
        }
        this.thrownDir = null;
        this.remainingThrowDuration = 0;

        this.addHealth(-damage);
        
        this.gameWorld.removeFlyingRobot(this.location);
        if (this.health > 0) {
            this.gameWorld.addRobot(this.location, this);
            this.controller.processTrapsAtLocation(this.location);
        } else {
            this.gameWorld.destroyRobot(this.getID());
        }

        setMovementCooldownTurns(this.movementCooldownTurns + GameConstants.HIT_TARGET_COOLDOWN);
        setActionCooldownTurns(this.actionCooldownTurns + GameConstants.HIT_TARGET_COOLDOWN);
        setTurningCooldownTurns(this.turningCooldownTurns + GameConstants.HIT_TARGET_COOLDOWN);
        this.gameWorld.getMatchMaker().addDamageAction(this.ID, damage);
        this.gameWorld.getMatchMaker().addRatNapAction(this.getID());

        this.gameWorld.getMatchMaker().addStunAction(this.ID, GameConstants.HIT_TARGET_COOLDOWN);
    }

    public void travelFlying(boolean isSecondMove) {
        if (this.thrownDir == null || this.health == 0) {
            return;
        }

        MapLocation newLoc = this.location.add(this.thrownDir);

        if (!this.gameWorld.getGameMap().onTheMap(newLoc)) {
            this.hitGround();
            return;
        } else if (this.gameWorld.getRobot(newLoc) != null
                && this.gameWorld.getRobot(newLoc).getType() == UnitType.CAT) {
            // cat feeding!
            this.gameWorld.removeFlyingRobot(this.location);
            this.addHealth(-this.getHealth()); // rat dies :(
            // put cat to sleep
            this.gameWorld.getRobot(newLoc).sleepTimeRemaining = GameConstants.CAT_SLEEP_TIME;
            return;
        } else if (this.gameWorld.getRobot(newLoc) != null || !this.gameWorld.isPassable(newLoc)) {
            this.hitTarget(isSecondMove);
            return;
        } else{
            this.gameWorld.removeFlyingRobot(this.location);
            this.gameWorld.addFlyingRobot(newLoc, this);
        }

        this.setInternalLocationOnly(newLoc);
    }

    /**
     * Attacks another location.
     * The type of attack is based on the robot type (specific methods above)
     * 
     * @param loc the location of the bot
     */
    public void attack(MapLocation loc) {
        switch (this.getType()) {
            case BABY_RAT, RAT_KING:
                bite(loc, -1);
                break;
            case CAT:
                scratch(loc);
                break;
            default:
                throw new RuntimeException("Unrecognized robot type: " + this.getType()); // should never happen
        }
    }

    /**
     * Attacks another location.
     * The type of attack is based on the robot type (specific methods above)
     * 
     * @param loc the location of the bot
     */
    public void attack(MapLocation loc, int cheese) {
        switch (this.getType()) {
            case BABY_RAT, RAT_KING:
                bite(loc, cheese);
                break;
            case CAT:
                scratch(loc);
                break;
            default:
                break;
        }
    }

    public int[] canPounce(MapLocation loc) {
        /*
         * Returns dx, dy of pounce if allowed; otherwise returns null
         */

        if (!this.canMoveCooldown())
            return null;

        // Must be a cat
        if (this.type != UnitType.CAT) {
            throw new RuntimeException("Unit must be a cat to pounce!");
        }

        // Target location must be on map and passable (no walls/dirt) and within max
        // pounce distnace
        boolean isWithinPounceDistance = (this.getLocation()
                .bottomLeftDistanceSquaredTo(loc) <= GameConstants.CAT_POUNCE_MAX_DISTANCE_SQUARED);
        if (!this.gameWorld.getGameMap().onTheMap(loc) || !this.gameWorld.isPassable(loc) || !isWithinPounceDistance) {
            return null;
        }

        for (MapLocation cornerToTest : getAllPartLocations()) {
            // attempt pounce that matches cornerToTest to target location
            Direction directionFromCornerToTestToCenter = cornerToTest.directionTo(this.getLocation());

            // dx and dy from bottom left corner
            // assuming getLocation returns the bottom left corner of the cat
            int dx = directionFromCornerToTestToCenter.dx + (loc.x - this.getLocation().x);
            int dy = directionFromCornerToTestToCenter.dy + (loc.y - this.getLocation().y);
            boolean validLandingTiles = true;

            // check passability of all landing tiles (and no cat)
            for (MapLocation tile : this.getAllPartLocations()) {
                MapLocation landingTile = tile.translate(dx, dy);
                if (!this.gameWorld.getGameMap().onTheMap(landingTile)) {
                    // will pounce to a tile off map
                    validLandingTiles = false;
                } else if (!this.gameWorld.isPassable(landingTile)) {
                    // will pounce into impassable loc
                    validLandingTiles = false;
                } else if (this.gameWorld.getRobot(landingTile) != null
                        && this.gameWorld.getRobot(landingTile).getType().isCatType()) {
                    // will land on another cat
                    validLandingTiles = false;
                } else if (this.gameWorld.getRobot(landingTile) != null
                        && this.gameWorld.getRobot(landingTile).getType().isRatKingType()) {
                    // will land on a rat king
                    validLandingTiles = false;
                }
            }
            if (validLandingTiles) {
                int[] pounceTraj = { dx, dy };
                return pounceTraj;
            }

        }
        return null;
    }

    public void pounce(int[] delta) {
        int dx = delta[0];
        int dy = delta[1];

        MapLocation[] oldLocs = this.getAllPartLocations();

        for (MapLocation partLoc : oldLocs) {
            // shift location by dx, dy
            MapLocation translatedLoc = partLoc.translate(dx, dy);
            InternalRobot crushedRobot = this.gameWorld.getRobot(translatedLoc);

            if (crushedRobot != null && (crushedRobot.getID() != this.ID)) {
                // destroy robot
                if (crushedRobot.isCarryingRobot()) {
                    InternalRobot carriedRobot = crushedRobot.getRobotBeingCarried();
                    carriedRobot.addHealth(-carriedRobot.getHealth());
                }
                crushedRobot.addHealth(-crushedRobot.getHealth());

            }
        }

        // actually translate the cat
        this.translateLocation(dx, dy);

        MapLocation[] newLocs = this.getAllPartLocations();

        for (MapLocation partLoc : newLocs) {
            this.controller.processTrapsAtLocation(partLoc);
        }

        // incur double the movement cooldown
        this.addMovementCooldownTurns(this.dir);
        this.addMovementCooldownTurns(this.dir);
    }

    public MapLocation getCatCornerByChirality() {
        // returns corner to use when chirality matters
        MapSymmetry symmetry = this.gameWorld.getGameMap().getSymmetry();
        MapLocation chiralityCorner;

        if (this.chirality == 0) {
            chiralityCorner = this.getLocation();
        } else {
            switch (symmetry) {
                case VERTICAL:
                    chiralityCorner = this.getLocation().add(Direction.EAST);
                    break;
                case HORIZONTAL:
                    chiralityCorner = this.getLocation().add(Direction.NORTH);
                    break;
                case ROTATIONAL:
                    chiralityCorner = this.getLocation().add(Direction.NORTHEAST);
                    break;
                default:
                    throw new RuntimeException("Invalid symmetry");
            }
        }

        return chiralityCorner;
    }

    public MapLocation[] getAllCatLocationsByChirality() {
        // returns part locations in proper order based on cat chirality
        MapLocation startingCorner = getCatCornerByChirality();
        Direction rotateDir;

        MapLocation[] allPartLocations = new MapLocation[4];
        // Check each part of the robot to see if we can pounce so that the part lands
        // on the target location
        MapSymmetry symmetry = this.gameWorld.getGameMap().getSymmetry();

        if (chirality == 0) { // check in clockwise order
            rotateDir = Direction.NORTH;
        } else {
            switch (symmetry) {
                case VERTICAL:
                    rotateDir = Direction.NORTH;
                    break;
                case HORIZONTAL:
                    rotateDir = Direction.SOUTH;
                    break;
                case ROTATIONAL:
                    rotateDir = Direction.WEST;
                    break;
                default:
                    throw new RuntimeException("Invalid symmetry");
            }
        }

        for (int i = 0; i < 4; i += 1) {
            allPartLocations[i] = startingCorner;

            startingCorner = startingCorner.add(rotateDir);
            if (chirality == 0) {
                rotateDir = rotateDir.rotateRight();
                rotateDir = rotateDir.rotateRight();
            } else {
                rotateDir = rotateDir.rotateLeft();
                rotateDir = rotateDir.rotateLeft();
            }
        }

        return allPartLocations;
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

    private void clearAllMessages() {
        while (!incomingMessages.isEmpty()) {
            popMessage();
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

        // first robot of the round should cause the cheese mines to run
        this.gameWorld.runCheeseMines();

        // if rat is being carried
        if (this.getType() == UnitType.BABY_RAT && this.isGrabbedByRobot()) {

            // check if grabber has died
            if (this.getGrabbedByRobot().getHealth() <= 0) {
                this.getDropped(this.getGrabbedByRobot().getLocation());
            } else if (this.remainingCarriedDuration == 0) { // max carry time reached
                MapLocation dropLoc = this.getGrabbedByRobot().getLocation().add(this.getDirection());

                if (this.gameWorld.getGameMap().onTheMap(dropLoc)
                        && this.gameWorld.isPassable(dropLoc)
                        && this.gameWorld.getRobot(dropLoc) == null) {
                    // Wriggle free!
                    InternalRobot grabber = this.getGrabbedByRobot();
                    this.getDropped(dropLoc);
                    grabber.robotBeingCarried = null;
                } else {
                    swapGrabber();
                }

            } else {
                // set location to grabber location
                this.setInternalLocationOnly(this.getGrabbedByRobot().getLocation());
                remainingCarriedDuration -= 1;
            }
        }

        // if baby rat is being thrown
        if (this.getType() == UnitType.BABY_RAT && this.isBeingThrown()) {
            // decrement first since we already moved once on the round where throwing was
            // initiated?
            this.remainingThrowDuration -= 1;
            if (this.remainingThrowDuration == 0) { // max throw time reached
                this.hitGround();
            } else {
                if (!this.gameWorld.getHasTraveledRobot(this.ID)) {
                    this.travelFlying(false);
                    this.travelFlying(true); // This will call hitTarget or hitGround if we hit something
                }

                if (this.remainingThrowDuration == 1) {
                    this.hitGround(); // should make it hit ground right after travelling
                }
            }
        }

        // if rat is being carried or thrown, skip cooldown resets; same for a sleeping
        // cat
        boolean isSleepingCat = this.getType().isCatType() && this.sleepTimeRemaining > 0;

        if (!this.isGrabbedByRobot() && !this.isBeingThrown() && !isSleepingCat) {
            this.actionCooldownTurns = Math.max(0, this.actionCooldownTurns - GameConstants.COOLDOWNS_PER_TURN);
            this.turningCooldownTurns = Math.max(0, this.turningCooldownTurns - GameConstants.COOLDOWNS_PER_TURN);
            this.movementCooldownTurns = Math.max(0, this.movementCooldownTurns - GameConstants.COOLDOWNS_PER_TURN);
        }

        this.currentBytecodeLimit = this.getType().bytecodeLimit;
        this.gameWorld.getMatchMaker().startTurn(this.ID);
    }

    public void processEndOfTurn() {
        if (!this.isGrabbedByRobot() && !this.isBeingThrown()) {
            this.turnsSinceThrownOrDropped += 1;
        }

        // eat cheese if rat king
        if (this.type.isRatKingType() && this.gameWorld.getTeamInfo().getNumRatKings(this.getTeam()) > 0) {
            // rat king starves
            if (this.gameWorld.getTeamInfo().getCheese(team) < GameConstants.RAT_KING_CHEESE_CONSUMPTION) {
                this.addHealth(-GameConstants.RAT_KING_HEALTH_LOSS);
            } else {
                this.addCheese(-GameConstants.RAT_KING_CHEESE_CONSUMPTION);
            }
        }

        // cat algo
        if (this.type == UnitType.CAT && this.sleepTimeRemaining > 0) {
            this.gameWorld.getMatchMaker().addCatFeedAction(this.getID());
            this.sleepTimeRemaining -= 1;
        } else if (this.type == UnitType.CAT) {
            Direction[] nonCenterDirections = {Direction.WEST, Direction.NORTHWEST, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST};

            switch (this.catState) {
                case EXPLORE:
                    if (this.catTurnsStuck >= 4) {
                        // cat has been unable to move or dig or attack for 4+ turns
                        // start turning and then trying to dig or attack again
                                            
                        Direction random = nonCenterDirections[rand.nextInt(nonCenterDirections.length)];

                        if (this.controller.canTurn()) {
                            try {
                                this.controller.turn(random);
                            } catch (GameActionException e) {
                            }
                        }
                    } else if (this.catTurnsStuck == 0) {
                        // cat not stuck and ready to move! let's set our eyes on the next waypoint
                        MapLocation waypoint = catWaypoints[currentWaypoint];

                        if (getCatCornerByChirality().equals(waypoint)) {
                            // reached waypoint, enter brief attack phase
                            if (currentWaypoint == previousWaypoint) { // just returned to explore phase from attack
                                                                       // phase, move on to next waypoint
                                currentWaypoint = (currentWaypoint + 1) % catWaypoints.length;
                            } else { // first time reaching this waypoint, attack!
                                previousWaypoint = currentWaypoint;
                                this.catState = CatStateType.ATTACK;
                                break;
                            }
                        }
                        this.catTargetLoc = catWaypoints[currentWaypoint];

                        this.dir = this.gameWorld.getBfsDir(getCatCornerByChirality(), this.catTargetLoc,
                                this.chirality);

                        if (dir == null || dir == Direction.CENTER) {
                            dir = this.location.directionTo(this.catTargetLoc);
                        }
                    }

                    // now that we've established our facing direction, try moving
                    if (this.controller.canMove(this.dir)) {
                        try {
                            this.controller.move(this.dir);
                            this.catTurnsStuck = 0;
                        } catch (GameActionException e) {
                        }
                    } else { // cannot move that way, remove dirt or attack whatever is blocking us
                        boolean isStuck = true; // represents whether cat is stuck without being able to dig or without
                                                // being able to attack

                        for (MapLocation partLoc : this.getAllPartLocations()) {
                            MapLocation nextLoc = partLoc.add(this.dir);

                            if (this.controller.canRemoveDirt(nextLoc)) {

                                try {
                                    this.controller.removeDirt(nextLoc);
                                    this.addActionCooldownTurns(GameConstants.CAT_DIG_ADDITIONAL_COOLDOWN);
                                    isStuck = false;
                                } catch (GameActionException e) {
                                    continue;
                                }

                            } else if (this.controller.canAttack(nextLoc)) {
                                try {
                                    this.controller.attack(nextLoc);
                                    isStuck = false;
                                } catch (GameActionException e) {
                                    continue;
                                }
                            }
                        }

                        // try pouncing out
                        if (isStuck) {
                            // try pouncing
                            int[] pounceTraj = null;
                            MapLocation twoTilesAway = this.getCatCornerByChirality().add(dir).add(dir);
                            pounceTraj = canPounce(twoTilesAway);
                            if (canMoveCooldown() && pounceTraj != null) {
                                this.pounce(pounceTraj);
                                isStuck = false;
                            }
                        }

                        // give up
                        if (isStuck) {
                            this.catTurnsStuck += 1;
                        } else {
                            this.catTurnsStuck = 0;
                        }
                    }
                    break;

                case ATTACK:
                    this.catTurns += 1; // increment number of turns spent in attack mode
                    if (this.catTurns > 8) { // only allow attacking for 8 turns
                        // return to exploring
                        this.catTurns = 0;
                        this.catState = CatStateType.EXPLORE;
                        break;
                    }

                    // first listen for squeaks and take first squeak heard on this turn
                    Message squeak = getFrontMessage();
                    clearAllMessages();
                    RobotInfo[] nearbyRobots = this.controller.senseNearbyRobots();

                    if (squeak != null && this.getLocation().directionTo(squeak.getSource()) != Direction.CENTER) {
                        // get distracted and turn towards squeak
                        this.dir = this.getLocation().directionTo(squeak.getSource());
                    }

                    // next look for rats in our current facing direction
                    boolean sensedRat = false; // whether we have a rat to target
                    // prioritize looking for target rat
                    if (this.catTarget != null) {
                        for (RobotInfo r : nearbyRobots) {
                            if (r.getID() == this.catTarget.getID()) {
                                sensedRat = true;
                                this.catTargetLoc = this.catTarget.getLocation();
                                break;
                            }
                        }
                    }
                    if (!sensedRat) { // if we couldn't find target rat or there was no target rat look for one in our
                                      // current vision cone
                        // reset cat target
                        this.catTarget = null;
                        this.catTargetLoc = null;

                        // look for new target
                        for (RobotInfo r : nearbyRobots) {
                            if (r.getType().isBabyRatType() || r.getType().isRatKingType()) {
                                sensedRat = true;
                                this.catTarget = r;
                                this.catTargetLoc = this.catTarget.getLocation();
                            }
                        }

                    }
                    if (this.catTargetLoc != null) {
                        // we have a target location!

                        // first try attacking
                        if (this.controller.canAttack(this.catTarget.getLocation())) {
                            try {
                                this.controller.attack(this.catTarget.getLocation());
                                break;
                            } catch (GameActionException e) {
                            }
                        } else {
                            // try pouncing
                            int[] pounceTraj = null;
                            pounceTraj = canPounce(this.catTargetLoc);

                            if (canMoveCooldown() && pounceTraj != null) {
                                this.pounce(pounceTraj);
                                break;
                            }
                            // if pounce failed, try moving in the direction of the target
                            else {
                                dir = this.gameWorld.getBfsDir(getCatCornerByChirality(), this.catTargetLoc,
                                        this.chirality);
                                if (dir == null || dir == Direction.CENTER) {
                                    dir = this.location.directionTo(this.catTargetLoc);
                                }
                                if (this.controller.canMove(this.dir)) {
                                    try {
                                        this.controller.move(this.dir);
                                        this.catTurnsStuck = 0;
                                        break;
                                    } catch (GameActionException e) {
                                    }
                                } else {
                                    // couldn't move :(

                                    // remove dirt if possible
                                    for (MapLocation partLoc : this.getAllPartLocations()) {
                                        MapLocation nextLoc = partLoc.add(this.dir);

                                        if (this.controller.canRemoveDirt(nextLoc)) {
                                            try {
                                                this.controller.removeDirt(nextLoc);
                                                this.addActionCooldownTurns(GameConstants.CAT_DIG_ADDITIONAL_COOLDOWN);
                                                break;
                                            } catch (GameActionException e) {
                                                continue;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // if we never successfully did anything, just rotate
                    if (this.controller.canTurn()) {
                        try {
                            if (this.chirality == 0)
                                this.controller.turn(this.dir.rotateRight());
                            else
                                this.controller.turn(this.dir.rotateLeft());
                        } catch (GameActionException e) {
                        }
                    }

                    break;
            }
        }

        // indicator strings!
        if (!indicatorString.equals("")) {
            this.gameWorld.getMatchMaker().addIndicatorString(this.ID, this.indicatorString);
        }

        this.gameWorld.getMatchMaker().endTurn(this.ID, this.health, this.cheeseAmount, this.movementCooldownTurns,
                this.actionCooldownTurns, this.turningCooldownTurns, this.bytecodesUsed, this.location, this.dir,
                this.gameWorld.isCooperation());
        if (this.isCarryingRobot() && this.robotBeingCarried.getHealth() > 0)
            this.gameWorld.getMatchMaker().endTurn(this.robotBeingCarried.ID, this.robotBeingCarried.health,
                    this.robotBeingCarried.cheeseAmount, this.robotBeingCarried.movementCooldownTurns,
                    this.robotBeingCarried.actionCooldownTurns, this.robotBeingCarried.turningCooldownTurns,
                    this.robotBeingCarried.bytecodesUsed, this.location, this.robotBeingCarried.dir,
                    this.gameWorld.isCooperation());
        this.roundsAlive++;
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
