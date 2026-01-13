package battlecode.world;

import battlecode.common.*;
import battlecode.schema.*;
import battlecode.util.FlatHelpers;
import battlecode.util.TeamMapping;
import gnu.trove.TIntArrayList;

import com.google.flatbuffers.FlatBufferBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class contains the code for reading a flatbuffer map file and converting
 * it
 * to a proper LiveMap.
 */
public final class GameMapIO {
    /**
     * The loader we use if we can't find a map in the correct path.
     */
    private static final ClassLoader BACKUP_LOADER = GameMapIO.class.getClassLoader();

    /**
     * The file extension for battlecode 2026 match files.
     */
    public static final String MAP_EXTENSION = ".map26";

    /**
     * The package we check for maps in if they can't be found in the file system.
     */
    public static final String DEFAULT_MAP_PACKAGE = "battlecode/world/resources/";

    /**
     * Returns a LiveMap for a specific map.
     * If the map can't be found in the given directory, the package
     * "battlecode.world.resources" is checked as a backup.
     *
     * @param mapName name of map.
     * @param mapDir  directory to load the extra map from; may be null.
     * @return LiveMap for map
     * @throws IOException if the map fails to load or can't be found.
     */
    public static LiveMap loadMap(String mapName, File mapDir, boolean teamsReversed) throws IOException {
        final LiveMap result;

        final File mapFile = new File(mapDir, mapName + MAP_EXTENSION);
        if (mapFile.exists()) {
            result = loadMap(new FileInputStream(mapFile), teamsReversed);
        } else {
            final InputStream backupStream = BACKUP_LOADER
                    .getResourceAsStream(DEFAULT_MAP_PACKAGE + mapName + MAP_EXTENSION);
            if (backupStream == null) {
                throw new IOException("Can't load map: " + mapName + " from dir " + mapDir + " or default maps.");
            }
            result = loadMap(backupStream, teamsReversed);
        }

        if (!result.getMapName().equals(mapName)) {
            throw new IOException("Invalid map: name (" + result.getMapName()
                    + ") does not match filename (" + mapName + MAP_EXTENSION + ")");
        }

        return result;
    }

    public static LiveMap loadMapAsResource(final ClassLoader loader,
            final String mapPackage,
            final String map, final boolean teamsReversed) throws IOException {
        final InputStream mapStream = loader.getResourceAsStream(
                mapPackage + (mapPackage.endsWith("/") ? "" : "/") +
                        map + MAP_EXTENSION);

        if (mapStream == null) {
            throw new IOException("Can't load map: " + map + " from package " + mapPackage);
        }

        final LiveMap result = loadMap(mapStream, teamsReversed);

        if (!result.getMapName().equals(map)) {
            throw new IOException("Invalid map: name (" + result.getMapName()
                    + ") does not match filename (" + map + MAP_EXTENSION + ")");
        }

        return result;
    }

    /**
     * Load a map from an input stream.
     *
     * @param stream the stream to read from; will be closed after the map is read.
     * @return a map read from the stream
     * @throws IOException if the read fails somehow
     */
    public static LiveMap loadMap(InputStream stream, boolean teamsReversed) throws IOException {
        return Serial.deserialize(IOUtils.toByteArray(stream), teamsReversed);
    }

    /**
     * Write a map to a file.
     *
     * @param mapDir the directory to store the map in
     * @param map    the map to write
     * @throws IOException if the write fails somehow
     */
    public static void writeMap(LiveMap map, File mapDir) throws IOException {
        final File target = new File(mapDir, map.getMapName() + MAP_EXTENSION);

        IOUtils.write(Serial.serialize(map), new FileOutputStream(target));
    }

    /**
     * @param mapDir the directory to check for extra maps. May be null.
     * @return a set of available map names, including those built-in to
     *         battlecode-server.
     */
    public static List<String> getAvailableMaps(File mapDir) {
        final List<String> result = new ArrayList<>();

        // Load maps from the extra directory
        if (mapDir != null) {
            if (mapDir.isDirectory()) {
                // Files in directory
                for (File file : mapDir.listFiles()) {
                    String name = file.getName();
                    if (name.endsWith(MAP_EXTENSION)) {
                        result.add(name.substring(0, name.length() - MAP_EXTENSION.length()));
                    }
                }
            }
        }

        // Load built-in maps
        URL serverURL = GameMapIO.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            if (GameMapIO.class.getResource("GameMapIO.class").getProtocol().equals("jar")) {
                // We're running from a jar file.
                final ZipInputStream serverJar = new ZipInputStream(serverURL.openStream());

                ZipEntry ze;
                while ((ze = serverJar.getNextEntry()) != null) {
                    final String name = ze.getName();
                    if (name.startsWith(DEFAULT_MAP_PACKAGE) && name.endsWith(MAP_EXTENSION)) {
                        result.add(
                                name.substring(DEFAULT_MAP_PACKAGE.length(), name.length() - MAP_EXTENSION.length()));
                    }
                }
            } else {
                // We're running from class files.
                final String[] resourceFiles = new File(BACKUP_LOADER.getResource(DEFAULT_MAP_PACKAGE).toURI()).list();

                for (String file : resourceFiles) {
                    if (file.endsWith(MAP_EXTENSION)) {
                        result.add(file.substring(0, file.length() - MAP_EXTENSION.length()));
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            System.err.println("Can't load default maps: " + e.getMessage());
            e.printStackTrace();
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Prevent instantiation.
     */
    private GameMapIO() {
    }

    /**
     * Conversion from / to flatbuffers.
     */
    public static class Serial {
        /**
         * Load a flatbuffer map into a LiveMap.
         *
         * @param mapBytes the raw bytes of the map
         * @return a new copy of the map as a LiveMap
         */
        public static LiveMap deserialize(byte[] mapBytes, boolean teamsReversed) {
            battlecode.schema.GameMap rawMap = battlecode.schema.GameMap.getRootAsGameMap(
                    ByteBuffer.wrap(mapBytes));

            return Serial.deserialize(rawMap, teamsReversed);
        }

        /**
         * Write a map to a byte[].
         *
         * @param gameMap the map to write
         * @return the map as a byte[]
         */
        public static byte[] serialize(LiveMap gameMap) {
            FlatBufferBuilder builder = new FlatBufferBuilder();

            int mapRef = Serial.serialize(builder, gameMap);

            builder.finish(mapRef);

            return builder.sizedByteArray();
        }

        /**
         * Load a flatbuffer map into a LiveMap.
         *
         * @param raw the flatbuffer map pointer
         * @return a new copy of the map as a LiveMap
         */
        public static LiveMap deserialize(battlecode.schema.GameMap raw, boolean teamsReversed) {
            final int width = (int) (raw.size().x());
            final int height = (int) (raw.size().y());
            final MapLocation origin = new MapLocation(0, 0);
            final MapSymmetry symmetry = MapSymmetry.values()[raw.symmetry()];
            final int seed = raw.randomSeed();
            final int rounds = GameConstants.GAME_MAX_NUMBER_OF_ROUNDS;
            final String mapName = raw.name();
            int size = width * height;
            boolean[] wallArray = new boolean[size];
            boolean[] dirtArray = new boolean[size];
            boolean[] cheeseMineArray = new boolean[size];
            ArrayList<int[]> catWaypoints = new ArrayList<int[]>();
            ArrayList<Integer> catIds = new ArrayList<Integer>();
            int[] cheeseArray = new int[size];

            for (int i = 0; i < wallArray.length; i++) {
                wallArray[i] = raw.walls(i);
                dirtArray[i] = raw.dirt(i);
                cheeseArray[i] = raw.cheese(i); // raw.cheese(i);
            }

            VecTable cheeseMinesTable = raw.cheeseMines();

            for (int i = 0; i < cheeseMinesTable.xsLength(); i++) {
                int x = cheeseMinesTable.xs(i);
                int y = cheeseMinesTable.ys(i);

                int idx = x + width * y;
                cheeseMineArray[idx] = true;
            }

            int numCats = raw.catWaypointVecsLength();

            for (int i = 0; i < numCats; i++) {
                int catId = raw.catWaypointIds(i);
                VecTable waypointTable = raw.catWaypointVecs(i);
                int numWaypoints = waypointTable.xsLength();
                int[] waypoints = new int[numWaypoints];

                for (int j = 0; j < numWaypoints; j++) {
                    int x = waypointTable.xs(j);
                    int y = waypointTable.ys(j);
                    waypoints[j] = x + width * y;
                }

                catIds.add(catId);
                catWaypoints.add(waypoints);
            }

            ArrayList<RobotInfo> initBodies = new ArrayList<>();
            InitialBodyTable bodyTable = raw.initialBodies();
            initInitialBodiesFromSchemaBodyTable(bodyTable, initBodies, teamsReversed);

            RobotInfo[] initialBodies = initBodies.toArray(new RobotInfo[initBodies.size()]);


            return new LiveMap(
                    width, height, origin, seed, rounds, mapName, symmetry, wallArray, dirtArray,
                    cheeseMineArray, cheeseArray, catIds, catWaypoints, initialBodies);
        }

        /**
         * Write a map to a builder.
         *
         * @param builder the target builder
         * @param gameMap the map to write
         * @return the object reference to the map in the builder
         */
        public static int serialize(FlatBufferBuilder builder, LiveMap gameMap) {
            int name = builder.createString(gameMap.getMapName());
            int randomSeed = gameMap.getSeed();
            boolean[] wallArray = gameMap.getWallArray();
            boolean[] dirtArray = gameMap.getDirtArray();
            boolean[] cheeseMineArray = gameMap.getCheeseMineArray();
            int[] cheeseArray = gameMap.getCheeseArray();

            // Make body tables
            ArrayList<Integer> bodyIDs = new ArrayList<>();
            ArrayList<Byte> bodyTeamIDs = new ArrayList<>();
            ArrayList<Byte> bodyTypes = new ArrayList<>();
            ArrayList<Integer> bodyLocsXs = new ArrayList<>();
            ArrayList<Integer> bodyLocsYs = new ArrayList<>();
            ArrayList<Integer> bodyDirs = new ArrayList<>();
            ArrayList<Byte> bodyChiralities = new ArrayList<>();

            ArrayList<Boolean> wallArrayList = new ArrayList<>();
            ArrayList<Boolean> dirtArrayList = new ArrayList<>();
            ArrayList<Integer> cheeseMineXs = new ArrayList<>();
            ArrayList<Integer> cheeseMineYs = new ArrayList<>();
            ArrayList<Byte> cheeseArrayList = new ArrayList<>();

            for (RobotInfo robot : gameMap.getInitialBodies()) {
                bodyIDs.add(robot.ID);

                // start all robots facing map center 
                MapLocation mapCenter = new MapLocation((gameMap.getWidth() - 1) / 2,
                        (gameMap.getHeight() - 1) / 2);
                Direction robotDir = robot.location.directionTo(mapCenter);
                
                bodyDirs.add(FlatHelpers.getOrdinalFromDirection(robotDir));
                bodyTeamIDs.add(TeamMapping.id(robot.team));
                bodyTypes.add(FlatHelpers.getRobotTypeFromUnitType(robot.type));
                bodyChiralities.add((byte)robot.chirality);
 
                bodyLocsXs.add(robot.location.x);
                bodyLocsYs.add(robot.location.y);
            }

            for (int i = 0; i < gameMap.getWidth() * gameMap.getHeight(); i++) {
                wallArrayList.add(wallArray[i]);
                dirtArrayList.add(dirtArray[i]);
                if(cheeseMineArray[i]) {
                    int x = i % gameMap.getWidth();
                    int y = i / gameMap.getWidth();
                    cheeseMineXs.add(x);
                    cheeseMineYs.add(y);
                }
                cheeseArrayList.add((byte)cheeseArray[i]);
            }

            int[] catWaypointTableOffsets = new int[gameMap.getNumCats()];
            int[] catIDs = new int[gameMap.getNumCats()];

            for (int i = 0; i < gameMap.getNumCats(); i++) {
                int catID = gameMap.getCatWaypointIDs().get(i);
                catIDs[i] = catID;
                int[] rawWaypoints = gameMap.getCatWaypointsByID(catID);
                int[] waypointsXs = new int[rawWaypoints.length];
                int[] waypointsYs = new int[rawWaypoints.length];
                for (int w = 0; w < rawWaypoints.length; w++) {
                    int x = rawWaypoints[w] % gameMap.getWidth();
                    int y = rawWaypoints[w] / gameMap.getWidth();
                    waypointsXs[w] = x;
                    waypointsYs[w] = y;
                }
                int vecTableOffset = FlatHelpers.createVecTable(builder, new TIntArrayList(waypointsXs), new TIntArrayList(waypointsYs));
                catWaypointTableOffsets[i] = vecTableOffset;
            }

            int wallArrayInt = battlecode.schema.GameMap.createWallsVector(builder,
                    ArrayUtils.toPrimitive(wallArrayList.toArray(new Boolean[wallArrayList.size()])));
            int dirtArrayInt = battlecode.schema.GameMap.createDirtVector(builder,
                    ArrayUtils.toPrimitive(dirtArrayList.toArray(new Boolean[dirtArrayList.size()])));
            int cheeseArrayInt = battlecode.schema.GameMap.createCheeseVector(builder,
                    ArrayUtils.toPrimitive(cheeseArrayList.toArray(new Byte[cheeseArrayList.size()])));

            int wayPointOffsets = battlecode.schema.GameMap.createCatWaypointVecsVector(builder, catWaypointTableOffsets);
            int catIDOffsets = battlecode.schema.GameMap.createCatWaypointIdsVector(builder, catIDs);

            //convert cheese mine x and y array list to arrays
            TIntArrayList cheeseMineXsList = new TIntArrayList(cheeseMineXs.stream().mapToInt(i -> i).toArray());
            TIntArrayList cheeseMineYsList = new TIntArrayList(cheeseMineYs.stream().mapToInt(i -> i).toArray());
            int cheeseMinesOffset = FlatHelpers.createVecTable(builder, cheeseMineXsList, cheeseMineYsList);
            
            
            int spawnActionVectorOffset = createSpawnActionsVector(builder, bodyIDs, bodyLocsXs, bodyLocsYs, bodyDirs, bodyChiralities,
                    bodyTeamIDs, bodyTypes);

            int initialBodyOffset = InitialBodyTable.createInitialBodyTable(builder, spawnActionVectorOffset);

            // Build LiveMap for flatbuffer
            battlecode.schema.GameMap.startGameMap(builder);
            battlecode.schema.GameMap.addName(builder, name);

            battlecode.schema.GameMap.addSize(builder, Vec.createVec(builder, gameMap.getWidth(), gameMap.getHeight()));

            battlecode.schema.GameMap.addSymmetry(builder, gameMap.getSymmetry().ordinal());
            battlecode.schema.GameMap.addRandomSeed(builder, randomSeed);
            battlecode.schema.GameMap.addWalls(builder, wallArrayInt);
            battlecode.schema.GameMap.addDirt(builder, dirtArrayInt);
            battlecode.schema.GameMap.addCheese(builder, cheeseArrayInt);
            battlecode.schema.GameMap.addInitialBodies(builder, initialBodyOffset);
            battlecode.schema.GameMap.addCheeseMines(builder, cheeseMinesOffset);
            
            battlecode.schema.GameMap.addCatWaypointVecs(builder, wayPointOffsets);
            battlecode.schema.GameMap.addCatWaypointIds(builder, catIDOffsets);
            return battlecode.schema.GameMap.endGameMap(builder);
        }

        // ****************************
        // *** HELPER METHODS *********
        // ****************************

        private static void initInitialBodiesFromSchemaBodyTable(InitialBodyTable bodyTable,
                ArrayList<RobotInfo> initialBodies, boolean teamsReversed) {
            for (int i = 0; i < bodyTable.spawnActionsLength(); i++) {
                battlecode.schema.SpawnAction curSpawnAction = bodyTable.spawnActions(i);
                int curId = curSpawnAction.id();
                UnitType bodyType = FlatHelpers.getUnitTypeFromRobotType(curSpawnAction.robotType());
                int bodyX = curSpawnAction.x();
                int bodyY = curSpawnAction.y();
                int dirOrdinal = curSpawnAction.dir();
                int chirality = curSpawnAction.chirality();
                
                Direction dir = FlatHelpers.getDirectionFromOrdinal(dirOrdinal); 

                Team bodyTeam = TeamMapping.team(curSpawnAction.team());

                if (bodyType == UnitType.CAT) {
                    bodyTeam = Team.NEUTRAL;
                }
                
                if (teamsReversed) {
                    bodyTeam = bodyTeam.opponent();
                }

                int initialCheese = GameConstants.INITIAL_TEAM_CHEESE;
                RobotInfo carryingRobot = null;
                initialBodies.add(new RobotInfo(curId, bodyTeam, bodyType, bodyType.health, new MapLocation(bodyX, bodyY), dir, chirality, initialCheese, carryingRobot));
                
            }
        }

        private static int createSpawnActionsVector(FlatBufferBuilder builder, ArrayList<Integer> ids,
                ArrayList<Integer> xs, ArrayList<Integer> ys, ArrayList<Integer> dirs, ArrayList<Byte> chiralities, ArrayList<Byte> teams, ArrayList<Byte> types) {
            InitialBodyTable.startSpawnActionsVector(builder, ids.size());
            for (int i = 0; i < ids.size(); i++) {
                SpawnAction.createSpawnAction(builder, ids.get(i), xs.get(i), ys.get(i), dirs.get(i), chiralities.get(i), teams.get(i), types.get(i));
            }
            return builder.endVector();
        }

    }
}
