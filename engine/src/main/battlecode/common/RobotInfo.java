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
     * The current location of the robot.
     */
    public final Direction direction;

    /**
     * Robot chirality (used by cat only).
     */
    public final int chirality;

    /**
     * The current cheese this robot holds
     */
    public final int cheeseAmount;
  
    /**
     * The current robot being carried by this robot, or null if not carrying any robots.
     */
    public final RobotInfo carryingRobot;

    public RobotInfo(int ID, Team team, UnitType type, int health, MapLocation location, Direction direction, int chirality, int cheeseAmount, RobotInfo carryingRobot) {
        if (team == null) {
            throw new IllegalArgumentException("Team in RobotInfo constructor cannot be null");
        } else if (type == null) {
            throw new IllegalArgumentException("UnitType in RobotInfo constructor cannot be null");
        } else if (location == null) {
            throw new IllegalArgumentException("MapLocation in RobotInfo constructor cannot be null");
        } else if (direction == null) {
            throw new IllegalArgumentException("Direction in RobotInfo constructor cannot be null");
        }

        this.ID = ID;
        this.team = team;
        this.type = type;
        this.health = health;
        this.location = location;
        this.direction = direction;
        this.chirality = chirality;
        this.cheeseAmount = cheeseAmount;
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
        return this.team;
    }

    /**
     * Returns the health of this robot.
     *
     * @return the health of this robot
     */
    public int getHealth() {
        return this.health;
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
     * Returns the direction of this robot.
     *
     * @return the direction of this robot
     */
    public Direction getDirection() {
        return this.direction;
    }

    /**
     * Returns the chirality of this robot.
     *
     * @return the chirality of this robot
     */
    public int getChirality() {
        return this.chirality;
    }

    /**
     * Returns this robot's type.
     * 
     * @return the type of this robot.
     */
    public UnitType getType() {
        return this.type;
    }

    /**
     * Returns the cheese amount of this robot.
     * 
     * @return the cheese amount of the robot
     */
    public int getRawCheeseAmount() {
        return this.cheeseAmount;
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
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RobotInfo robotInfo = (RobotInfo) o;

        if (ID != robotInfo.ID)
            return false;
        if (team != robotInfo.team)
            return false;
        if (health != robotInfo.health)
            return false;
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
                ", raw cheese amount=" + cheeseAmount +
                ", carrying=" + carryingRobot +
                '}';
    }
}
