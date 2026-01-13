package battlecode.world;

import battlecode.common.*;

import java.io.File;
import java.util.*;

/**
 * Build and validate maps easily.
 */
public class MapBuilder {

    public String name;
    public int width;
    public int height;
    public MapLocation origin;
    public int seed;
    private MapSymmetry symmetry;
    private boolean[] wallArray;
    private boolean[] dirtArray;
    private boolean[] cheeseMineArray;
    private int[] cheeseArray;
    private ArrayList<Integer> catIDs;
    private ArrayList<int[]> catWaypoints;

    // private int idCounter;

    private List<RobotInfo> bodies;

    public MapBuilder(String name, int width, int height, int originX, int originY, int seed) {
        assert(originX == 0);
        assert(originY == 0);
        this.name = name;
        this.width = width;
        this.height = height;
        this.origin = new MapLocation(originX, originY);
        this.seed = seed;
        this.bodies = new ArrayList<>();

        // default values
        this.symmetry = MapSymmetry.ROTATIONAL;
        // this.idCounter = 0;
        
        int numSquares = width * height;

        this.wallArray = new boolean[numSquares];
        this.dirtArray = new boolean[numSquares];
        this.cheeseMineArray = new boolean[numSquares];
        this.cheeseArray = new int[numSquares];
        this.catIDs = new ArrayList<Integer>();
        this.catWaypoints = new ArrayList<int[]>();
    }

    // ********************
    // BASIC METHODS
    // ********************

    /**
     * Convert location to index. Critical: must conform with GameWorld.indexToLocation.
     * @param x
     * @param y
     * @return
     */
    private int locationToIndex(int x, int y) {
        return x + y * width;
    }

    // private int locationToIndex(MapLocation loc) {
    //     return loc.x + loc.y * width;
    // }

    // public void setWall(int x, int y, boolean value) {
    //     this.wallArray[locationToIndex(x, y)] = value;
    // }

    // public void setCloud(int x, int y, boolean value) {
    //     this.cloudArray[locationToIndex(x, y)] = value;
    // }

    // public void setCurrent(int x, int y, int value) {
    //     this.currentArray[locationToIndex(x, y)] = value;
    // }

    // public void setIsland(int x, int y, int value) {
    //     this.islandArray[locationToIndex(x, y)] = value;
    // }

    // public void setResource(int x, int y, int value) {
    //     this.resourceArray[locationToIndex(x, y)] = value;
    // }

    // public void setSpawnZone(int x, int y, int value) {
    //     this.spawnZoneArray[locationToIndex(x, y)] = value;
    // }

    public void setSymmetry(MapSymmetry symmetry) {
        this.symmetry = symmetry;
    }

    // ********************
    // SYMMETRY METHODS
    // ********************

    public int symmetricY(int y) {
        return symmetricY(y, symmetry);
    }

    public int symmetricX(int x) {
        return symmetricX(x, symmetry);
    }

    public int symmetricY(int y, MapSymmetry symmetry) {
        switch (symmetry) {
            case VERTICAL:
                return y;
            case HORIZONTAL:
            case ROTATIONAL:
            default:
                return height - 1 - y;
        }
    }

    public int symmetricX(int x, MapSymmetry symmetry) {
        switch (symmetry) {
            case HORIZONTAL:
                return x;
            case VERTICAL:
            case ROTATIONAL:
            default:
                return width - 1 - x;
        }
    }

    public MapLocation symmetryLocation(MapLocation p) {
        return new MapLocation(symmetricX(p.x), symmetricY(p.y));
    }

    public void setSymmetricWalls(int x, int y, boolean value) {
        this.wallArray[locationToIndex(x, y)] = value;
        this.wallArray[locationToIndex(symmetricX(x), symmetricY(y))] = value;
    }

    // public void setSymmetricCloud(int x, int y, boolean value) {
    //     this.cloudArray[locationToIndex(x, y)] = value;
    //     this.cloudArray[locationToIndex(symmetricX(x), symmetricY(y))] = value;
    // }

    // private int getSymmetricCurrent(int value) {
    //     Direction currentDirection = Direction.DIRECTION_ORDER[value];
    //     return currentDirection.opposite().getDirectionOrderNum();
    // }

    // public void setSymmetricCurrent(int x, int y, int value) {
    //     this.currentArray[locationToIndex(x, y)] = value;
    //     this.currentArray[locationToIndex(symmetricX(x), symmetricY(y))] = getSymmetricCurrent(value);
    // }

    // private int getSymmetricIsland(int id) {
    //     return id == 0 ? 0 : this.islandArray.length-id;
    // }

    // public void setSymmetricIsland(int x, int y, int id) {
    //     this.islandArray[locationToIndex(x, y)] = id;
    //     this.islandArray[locationToIndex(symmetricX(x), symmetricY(y))] = getSymmetricIsland(id);
    // }

    // public void setSymmetricResource(int x, int y, int id) {
    //     this.resourceArray[locationToIndex(x, y)] = id;
    //     this.resourceArray[locationToIndex(symmetricX(x), symmetricY(y))] = id;
    // }

    // ********************
    // BUILDING AND SAVING
    // ********************

    public LiveMap build() {
        return new LiveMap(width, height, origin, seed, 2000, name, symmetry,
            wallArray, dirtArray, cheeseMineArray, cheeseArray, catIDs, catWaypoints,
            bodies.toArray(new RobotInfo[bodies.size()]));
    }

    /**
     * Saves the map to the specified location.
     * @param pathname
     * @throws Exception
     */
    public void saveMap(String pathname) throws Exception {
        // validate
        LiveMap lm = this.build();
        lm.assertIsValid();
        GameMapIO.writeMap(lm, new File(pathname));
    }



    public boolean onTheMap(MapLocation loc) {
        return loc.x >= 0 && loc.y >= 0 && loc.x < width && loc.y < height;
    }

    public MapLocation indexToLocation(int idx) {
        return new MapLocation(idx % this.width,
                               idx / this.width);
    }

    /**
     * @return the list of symmetries, empty if map is invalid
     */
    // private ArrayList<MapSymmetry> getSymmetry(RobotInfo[] robots) {
    //     ArrayList<MapSymmetry> possible = new ArrayList<MapSymmetry>();
    //     possible.add(MapSymmetry.ROTATIONAL);
    //     possible.add(MapSymmetry.HORIZONTAL);
    //     possible.add(MapSymmetry.VERTICAL);
    //     for (int x = 0; x < width; x++) {
    //         for (int y = 0; y < height; y++) {
    //             MapLocation current = new MapLocation(x, y);
    //             int curIdx = locationToIndex(current.x, current.y);
    //             for (int i = possible.size() - 1; i >= 0; i--) { // iterating backwards so we can remove in the loop
    //                 MapSymmetry symmetry = possible.get(i);
    //                 MapLocation symm = new MapLocation(symmetricX(x, symmetry), symmetricY(y, symmetry));
    //                 int symIdx = locationToIndex(symm.x, symm.y);

    //                 if (wallArray[curIdx] != wallArray[symIdx]) {
    //                     possible.remove(symmetry);
    //                 } else if (dirtArray[curIdx] != dirtArray[symIdx]) {
    //                     possible.remove(symmetry);
    //                 } else if (cheeseMineArray[curIdx] != cheeseMineArray[symIdx]) {
    //                     possible.remove(symmetry);
    //                 } else if (cheeseArray[curIdx] != cheeseArray[symIdx]) {
    //                     possible.remove(symmetry);
    //                 }
    //             }
    //         }
    //     }
    //     return possible;
    // }

    // private boolean symmetricTeams(Team a, Team b) {
    //     return a != b;
    // }
}
