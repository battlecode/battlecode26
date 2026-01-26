package battlecode.common;

public class MapInfo {

    private MapLocation loc;

    private boolean isPassable;

    private RobotInfo flyingRobot;

    private boolean isWall;

    private boolean isDirt;
    
    private int cheeseAmount;

    //You can only see traps from your own team
    private TrapType trap;

    private boolean hasCheeseMine;

    public MapInfo(MapLocation loc, boolean isPassable, RobotInfo flyingRobot, boolean isWall, boolean isDirt, int cheeseAmount, TrapType trap, boolean hasCheeseMine) {
        if (loc == null) {
            throw new IllegalArgumentException("MapLocation in MapInfo constructor cannot be null");
        } else if (trap == null) {
            throw new IllegalArgumentException("TrapType in MapInfo constructor cannot be null, use TrapType.NONE instead.");
        }

        this.loc = loc;
        this.isPassable = isPassable;
        this.flyingRobot = flyingRobot;
        this.isWall = isWall;
        this.isDirt = isDirt;
        this.cheeseAmount = cheeseAmount;
        this.trap = trap;
        this.hasCheeseMine = hasCheeseMine;
    }

    /**
     * Returns if this square is passable.
     * 
     * @return whether this square is passable
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean isPassable() {
        return isPassable;
    }

    /**
     * Returns info of a flying robot at this location
     * 
     * @return RobotInfo for the flying robot at this location (or null if no flying robot is present)
     * 
     * @battlecode.doc.costlymethod
     */
    public RobotInfo flyingRobot() {
        return flyingRobot;
    }

    /**
     * Returns if this square is a wall.
     * 
     * @return whether this square is a wall
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean isWall() {
        return isWall;
    }

    /**
     * Returns if this square is a dirt.
     * 
     * @return whether this square is dirt
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean isDirt() {
        return isDirt;
    }

    /**
     * Returns if this square has a cheese mine.
     * 
     * @return whether this square has a cheese mine
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean hasCheeseMine() {
        return hasCheeseMine;
    }

    /**
     * Returns the trap on this square, or TrapType.NONE if there is no trap.
     * 
     * @return the trap on this square
     * 
     * @battlecode.doc.costlymethod
     */
    public TrapType getTrap() {
        return trap;
    }

    /**
     * Returns the amount of cheese on this square (guaranteed to be 0 if no rats died here and not near a cheese mine).
     * 
     * @return the amount of cheese on this square
     * 
     * @battlecode.doc.costlymethod
     */
    public int getCheeseAmount() {
        return cheeseAmount;
    }

    /**
     * Returns the location of this square
     * 
     * @return the location of this square
     * 
     * @battlecode.doc.costlymethod
     */
    public MapLocation getMapLocation() {
        return loc;
    }

    public String toString() {
        return "Location{" +
                "loc=" + loc.toString() +
                (isWall ? ", wall" : "") +
                (isDirt ? ", dirt" : "") +
                (hasCheeseMine ? ", with cheese mine" : "") +
                (isPassable ? ", passable" : ", not passable") +
                ", trap=" + trap +
                "}";
    }
}
