package battlecode.common;

/**
 * RobotInfo stores basic information that was 'sensed' of another Robot. This
 * info is ephemeral and there is no guarantee any of it will remain the same
 * between rounds.
 */
public class RobotInfo {

    /**
     * The unique ID of the robot.
     */
    public final int ID;

    /**
     * The Team that the robot is on.
     */
    public final Team team;

    /**
     * The type of the robot.
     */
    public final UnitType type;

    /**
     * The health of the robot.
     */
    public final int health;

    /**
     * The current location of the robot.
     */
    public final MapLocation location;

    /**
     * Whether or not the robot is a cat and is crouching
     */
    public final boolean crouching;

    /**
     * The current robot being carried by this robot, or null if not carrying any robots.
     */
    public final RobotInfo carryingRobot;

    public RobotInfo(int ID, Team team, UnitType type, int health, MapLocation location, int paintAmount, RobotInfo carryingRobot, boolean crouching) {
        super();
        this.ID = ID;
        this.team = team;
        this.type = type;
        this.health = health;
        this.location = location;
        this.crouching = type.isRatType() && crouching;
        this.carryingRobot = carryingRobot;
    }

    /**
     * Returns the ID of this robot.
     *
     * @return the ID of this robot
     */
    public int getID() {
        return this.ID;
    }

    /**
     * Returns the team that this robot is on.
     *
     * @return the team that this robot is on
     */
    public Team getTeam() {
        return team;
    }

    /**
     * Returns the health of this robot.
     *
     * @return the health of this robot
     */
    public int getHealth() {
        return health;
    }

    /**
     * Returns the location of this robot.
     *
     * @return the location of this robot
     */
    public MapLocation getLocation() {
        return this.location;
    }

    /**
     * Returns this robot's type.
     * 
     * @return the type of this robot.
     */
    public UnitType getType(){
        return this.type;
    }

    /**
     * Returns whether or not the robot is crouching
     *
     * @return if the robot is crouching
     */
    public boolean isCrouching(){
        return crouching;
    }

    /**
     * Returns the robot this robot is carrying, or null if not carrying a robot. 
     * 
     * @return the robot the robot is carrying, or null if not carrying a robot
     */
    public RobotInfo getCarryingRobot() {
        return this.carryingRobot;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RobotInfo robotInfo = (RobotInfo) o;

        if (ID != robotInfo.ID) return false;
        if (team != robotInfo.team) return false;
        if (health != robotInfo.health) return false;
        return location.equals(robotInfo.location);
    }

    @Override
    public int hashCode() {
        int result;
        result = ID;
        result = 31 * result + team.hashCode();
        result = 31 * result + health;
        result = 31 * result + location.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RobotInfo{" +
                "ID=" + ID +
                ", team=" + team +
                ", health=" + health +
                ", location=" + location +
                ", crouching=" + crouching +
                ", carrying=" + carryingRobot +
                '}';
    }
}
