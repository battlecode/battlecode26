package battlecode.common;

public class MapInfo {

    private MapLocation loc;

    private boolean isPassable;

    private boolean isWall;

    private boolean isDirt;

    private PaintType paint;

    private PaintType mark;

    private boolean hasRuin;

    private boolean isResourcePatternCenter;

    public MapInfo(MapLocation loc, boolean isPassable, boolean isWall, boolean isDirt, PaintType paint, PaintType mark, boolean isResourcePatternCenter){
        this.loc = loc;
        this.isPassable = isPassable;
        this.isWall = isWall;
        this.isDirt = isDirt;
        this.paint = paint;
        this.mark = mark;
        this.isResourcePatternCenter = isResourcePatternCenter;
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
     * Returns if this square is a wall.
     * 
     * @return whether this square is a wall
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean isDirt() {
        return isDirt;
    }

    /**
     * Returns if this square has a ruin.
     * 
     * @return whether this square has a ruin
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean hasRuin() {
        return hasRuin;
    }

    /**
     * Returns the paint value of this square
     * 
     * @return the paint value of this square
     * 
     * @battlecode.doc.costlymethod
     */
    public PaintType getPaint() {
        return paint;
    }

    /**
     * Returns the mark value of this square
     * 
     * @return the mark value of this square
     * 
     * @battlecode.doc.costlymethod
     */
    public PaintType getMark() {
        return mark;
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

    /**
     * Returns whether this tile is at the center of an ally resource pattern (regardless of whether the pattern is active yet)
     * 
     * @return Whether this is a resource pattern center
     */
    public boolean isResourcePatternCenter() {
        return isResourcePatternCenter;
    }

    public String toString(){
        return "Location{" +
                "loc=" + loc.toString() +
                (isWall ? ", wall" : "") +
                (isDirt ? ", dirt" : "") +
                (hasRuin ? ", with ruin" : "") +
                ", paint=" + paint.toString() +
                ", mark=" + mark.toString() +
                "}";
    }
}
