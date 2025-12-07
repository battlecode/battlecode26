package battlecode.world;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
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

    private int paintAmount;
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

        this.paintAmount = 0;

        this.controlBits = 0;
        this.currentBytecodeLimit = type.isRobotType() ? GameConstants.ROBOT_BYTECODE_LIMIT : GameConstants.TOWER_BYTECODE_LIMIT;
        this.bytecodesUsed = 0;

        this.roundsAlive = 0;
        this.actionCooldownTurns = type.actionCooldown;
        this.movementCooldownTurns = GameConstants.COOLDOWN_LIMIT;

        this.carryingRobot = null;
        this.grabbedByRobot = null;
        this.thrownDir = null;

        this.indicatorString = "";

        this.controller = new RobotControllerImpl(gameWorld, this);

        
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
    //     return this.offsetToCenter == Direction.CENTER;
    // }

    // public InternalRobot getCenterRobot(){
    //     MapLocation centerLocation = this.location.add(offsetToCenter);
    //     return this.gameWorld.getRobot(centerLocation);
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

    public MapLocation[] getAllPartLocations(){ 
        return this.getType().getAllLocations(this.location);
    }

    public MapLocation getDiedLocation() {
        return diedLocation;
    }

    public int getHealth() {
        return health;
    }

    public int getPaint() {
        return paintAmount;
    }

    public void addPaint(int amount) {
        int newPaintAmount = this.paintAmount + amount;
        if (newPaintAmount > this.type.paintCapacity) {
            this.paintAmount = this.type.paintCapacity;
        } else if (newPaintAmount < 0) {
            this.paintAmount = 0;
        } else {
            this.paintAmount = newPaintAmount;
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
                && cachedRobotInfo.paintAmount == paintAmount
                && cachedRobotInfo.location.equals(location)) {
            return cachedRobotInfo;
        }

        this.cachedRobotInfo = new RobotInfo(ID, team, type, health, location, paintAmount, carryingRobot != null ? carryingRobot.getRobotInfo() : null);
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
        return this.location.isWithinDistanceSquared(toSense, getVisionRadiusSquared(), this.dir, getVisionConeAngle(), this.type.usesTopRightLocationForDistance());
    }

    /**
     * Returns whether this robot can sense a given radius away.
     * 
     * @param radiusSquared the distance squared to sense
     */
    public boolean canSenseRadiusSquared(int radiusSquared) {
        return radiusSquared <= getVisionRadiusSquared();
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
     * @param loc the new location of the robot
     */
    public void setLocation(MapLocation loc) {
        this.gameWorld.moveRobot(getLocation(), loc);
        // this.gameWorld.getObjectInfo().moveRobot(this, loc);
        this.location = loc;
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
        int paintPercentage = (int) Math.round(this.paintAmount * 100.0/ this.type.paintCapacity);
        /* TODO this is paint depletion logic and can probably be removed
        if (paintPercentage < GameConstants.INCREASED_COOLDOWN_THRESHOLD && type.isRobotType()) {
            numActionCooldownToAdd += (int) Math.round(numActionCooldownToAdd
                    * (GameConstants.INCREASED_COOLDOWN_INTERCEPT + GameConstants.INCREASED_COOLDOWN_SLOPE * paintPercentage)
                    / 100.0);
        }
        */
        setActionCooldownTurns(this.actionCooldownTurns + numActionCooldownToAdd);
    }

    /**
     * Resets the movement cooldown.
     */
    public void addMovementCooldownTurns() {
        int movementCooldown = GameConstants.MOVEMENT_COOLDOWN;
        int paintPercentage = (int) Math.round(this.paintAmount * 100.0/ this.type.paintCapacity);
        /* TODO this is paint depletion logic and can probably be removed
        if (paintPercentage < GameConstants.INCREASED_COOLDOWN_THRESHOLD && type.isRobotType()) {
            movementCooldown += (int) Math.round(movementCooldown
                    * (GameConstants.INCREASED_COOLDOWN_INTERCEPT + GameConstants.INCREASED_COOLDOWN_SLOPE * paintPercentage)
                    / 100.0);
        }
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

    // *********************************
    // ****** ACTION METHODS *********
    // *********************************

    private int locationToInt(MapLocation loc) {
        return this.gameWorld.locationToIndex(loc);
    }

    public void scratch(MapLocation loc) {
        if(this.type != UnitType.CAT)
            throw new RuntimeException("Unit must be a cat!");

        // If there's a robot on the tile, deal large damage to it
        if(this.gameWorld.getRobot(loc) != null) {
            InternalRobot robot = this.gameWorld.getRobot(loc);
            if(this.team != robot.getTeam()) {
                robot.addHealth(-GameConstants.CAT_SCRATCH_DAMAGE);
                this.gameWorld.getMatchMaker().addAttackAction(robot.getID());
            }
        }

    }

    public void grabRobot(MapLocation loc) {
        if(!this.type.isThrowingType()) {
            throw new RuntimeException("Unit must be a rat to grab other rats");
        }else if(!loc.isAdjacentTo(this.getLocation())) {
            throw new RuntimeException("Can only grab adjacent robots");
        }else if (false) { // TODO
            throw new RuntimeException("Can only grab robots in front of us");
        }else if(this.isCarryingRobot()) {
            throw new RuntimeException("Already carrying a robot");
        }else if(this.isGrabbedByRobot()) { // This should never occur, since grabbed robots are on action cooldown
            throw new RuntimeException("Cannot grab while being carried");
        }

        if(this.gameWorld.getRobot(loc) != null && this.gameWorld.getRobot(loc).getType().isThrowableType() && !this.gameWorld.getRobot(loc).isBeingThrown()) {
            boolean canGrab = false;
            if(false) { // TODO replace with checking if the enemy robot is facing away from us
                canGrab = true; // We can always grab robots facing away from us
            }else if(this.team == this.gameWorld.getRobot(loc).getTeam()) {
                canGrab = true; // We can always grab allied robots
            }else if(this.gameWorld.getRobot(loc).getHealth() + GameConstants.HEALTH_GRAB_THRESHOLD < health) {
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
        if(!this.type.isThrowingType()) {
            throw new RuntimeException("Unit must be a rat to throw other rats");
        }else if(!this.isCarryingRobot()) {
            throw new RuntimeException("Not carrying a robot to throw");
        }
        if(!this.gameWorld.getGameMap().onTheMap(this.getLocation().add(dir))) {
            throw new RuntimeException("Cannot throw outside of map");
        }

        // Throw the robot
        this.carryingRobot.getThrown(dir);
        this.gameWorld.getMatchMaker().addThrowAction(this.carryingRobot.getID(), locationToInt(this.getLocation().add(dir)));
        this.carryingRobot = null;
    }

    private void getThrown(Direction dir) {
        this.grabbedByRobot = null;
        this.thrownDir = dir;
        this.setLocation(this.getLocation().add(dir));
        this.gameWorld.addRobot(this.getLocation(), this);
    }

    public void hitGround() {
        this.thrownDir = null;
        this.addHealth(-GameConstants.THROW_DAMAGE-GameConstants.THROW_DAMAGE_PER_TURN * (this.actionCooldownTurns - GameConstants.THROW_STUN_DURATION) / GameConstants.COOLDOWNS_PER_TURN);
        this.movementCooldownTurns = GameConstants.THROW_STUN_DURATION;
        this.actionCooldownTurns = GameConstants.THROW_STUN_DURATION;
    }

    public void travelFlying() {
        MapLocation newLoc = this.getLocation().add(this.thrownDir);
        if(!this.gameWorld.getGameMap().onTheMap(newLoc) || this.gameWorld.getRobot(newLoc) != null || !this.gameWorld.isPassable(newLoc)) {
            this.hitGround();
            return;
        }

        this.setLocation(newLoc);
        
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
        switch(this.getType()) {
            case RAT:
                // TODO bite(loc);
                break; 
            case CAT:
                scratch(loc);
                break;
            default:
                // TODO
                break;
        }
    }

    // *********************************
    // ***** COMMUNICATION METHODS *****
    // *********************************

    public int getSentMessagesCount() {
        return sentMessagesCount;
    }

    public Message[] getMessages(){
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

    private void addMessage(Message message) {
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
        if (this.type.paintPerTurn != 0 )
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
        this.currentBytecodeLimit = this.type.isRobotType() ? GameConstants.ROBOT_BYTECODE_LIMIT : GameConstants.TOWER_BYTECODE_LIMIT;
        this.gameWorld.getMatchMaker().startTurn(this.ID);
    }

    public void processEndOfTurn() {
        // indicator strings!
        if (!indicatorString.equals("")) {
            this.gameWorld.getMatchMaker().addIndicatorString(this.ID, this.indicatorString);
        }

        this.gameWorld.getMatchMaker().endTurn(this.ID, this.health, this.paintAmount, this.movementCooldownTurns, this.actionCooldownTurns, this.bytecodesUsed, this.location);
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
