package battlecode.world;

import battlecode.common.*;
import battlecode.instrumenter.profiler.ProfilerCollection;
import battlecode.server.ErrorReporter;
import battlecode.server.GameMaker;
import battlecode.server.GameState;
import battlecode.world.control.RobotControlProvider;

import java.util.*;

/**
 * The primary implementation of the GameWorld interface for containing and
 * modifying the game map and the objects on it.
 */
public class GameWorld {
    /**
     * The current round we're running.
     */
    protected int currentRound;

    /**
     * Whether we're running.
     */
    protected boolean running = true;
    protected boolean isCooperation = true;

    protected final IDGenerator idGenerator;
    protected final GameStats gameStats;

    private boolean[] walls;
    private boolean[] dirt;

    private int[] cheeseAmounts;
    private InternalRobot[][] robots;
    private Trap[] trapLocations;
    private ArrayList<Trap>[] trapTriggers;
    private HashMap<TrapType, int[]> trapCounts; // maps trap type to counts for each team
    private final LiveMap gameMap;
    private final TeamInfo teamInfo;
    private final ObjectInfo objectInfo;
    private boolean hasRunCheeseMinesThisRound; // whether we've run the cheese mines yet
    private HashSet<Integer> hasTraveledIDs; // ids of robots that have traveled this ronud
    
    private int[] currentNumberUnits = { 0, 0 };

    private Map<Team, ProfilerCollection> profilerCollections;

    private final RobotControlProvider controlProvider;
    Random rand;
    private final GameMaker.MatchMaker matchMaker;

    // Whether there is a ruin on each tile, indexed by location
    private boolean[] allCheeseMinesByLoc;
    // list of all cheese mines
    private ArrayList<CheeseMine> cheeseMines;
    private CheeseMine[] cheeseMineLocs;

    // bfs map
    private Direction[][] bfs_map_0;
    private Direction[][] bfs_map_1;

    private int numCats;

    private int[][] sharedArray;
    private int[][] persistentArray;

    public int symmetricY(int y) {
        return symmetricY(y, gameMap.getSymmetry());
    }

    public int symmetricX(int x) {
        return symmetricX(x, gameMap.getSymmetry());
    }

    public int symmetricY(int y, MapSymmetry symmetry) {
        switch (symmetry) {
            case VERTICAL:
                return y;
            case HORIZONTAL:
            case ROTATIONAL:
            default:
                return gameMap.getHeight() - 1 - y;
        }
    }

    public int symmetricX(int x, MapSymmetry symmetry) {
        switch (symmetry) {
            case HORIZONTAL:
                return x;
            case VERTICAL:
            case ROTATIONAL:
            default:
                return gameMap.getWidth() - 1 - x;
        }
    }


    public Direction flipDirBySymmetry(Direction d){
        MapSymmetry symmetry = this.gameMap.getSymmetry();
        int dx = d.getDeltaX();
        int dy = d.getDeltaY();
        switch (symmetry) {
            case HORIZONTAL:
                dy *= -1;
                break;
            case VERTICAL:
                dx *= -1;
                break;
            case ROTATIONAL:
                dx *= -1;
                dy *= -1;
                break;
        } 

        return Direction.fromDelta(dx, dy);
    }

    public void runCheeseMines(){
        if (hasRunCheeseMinesThisRound)
            return;

        for (CheeseMine mine : this.cheeseMines) {
            spawnCheese(mine);
        }
        hasRunCheeseMinesThisRound = true;
    }

    public MapLocation symmetryLocation(MapLocation p) {
        return new MapLocation(symmetricX(p.x), symmetricY(p.y));
    }

    @SuppressWarnings("unchecked")
    public GameWorld(LiveMap gm, RobotControlProvider cp, GameMaker.MatchMaker matchMaker) {
        int width = gm.getWidth();
        int height = gm.getHeight();
        int numSquares = width * height;
        this.walls = gm.getWallArray();
        this.dirt = gm.getDirtArray();
        this.cheeseAmounts = gm.getCheeseArray();
        this.trapLocations = new Trap[numSquares]; // We guarantee that no maps will contain traps at t = 0
        this.robots = new InternalRobot[width][height]; // if represented in cartesian, should be height-width, but this
                                                        // should allow us to index x-y
        this.hasRunCheeseMinesThisRound = false;
        this.currentRound = 0;
        this.idGenerator = new IDGenerator(gm.getSeed());
        this.gameStats = new GameStats();
        this.gameMap = gm;
        this.objectInfo = new ObjectInfo(gm);
        this.trapCounts = new HashMap<>();
        trapCounts.put(TrapType.CAT_TRAP, new int[2]);
        trapCounts.put(TrapType.RAT_TRAP, new int[2]);
        trapTriggers = new ArrayList[numSquares];
        for (int i = 0; i < trapTriggers.length; i++) {
            trapTriggers[i] = new ArrayList<>();
        }

        this.profilerCollections = new HashMap<>();

        this.controlProvider = cp;
        this.rand = new Random(this.gameMap.getSeed());
        this.matchMaker = matchMaker;

        this.controlProvider.matchStarted(this);

        this.teamInfo = new TeamInfo(this);
        this.teamInfo.addCheese(Team.A, GameConstants.INITIAL_TEAM_CHEESE);
        this.teamInfo.addCheese(Team.B, GameConstants.INITIAL_TEAM_CHEESE);

        // Write match header at beginning of match
        this.matchMaker.makeMatchHeader(this.gameMap);
        
        this. hasTraveledIDs = new HashSet<>();
        this.allCheeseMinesByLoc = gm.getCheeseMineArray();
        this.cheeseMines = new ArrayList<CheeseMine>();
        this.cheeseMineLocs = new CheeseMine[numSquares];

        this.numCats = 0;

        for (int i = 0; i < numSquares; i++) {
            if (this.allCheeseMinesByLoc[i]) {
                CheeseMine newMine = new CheeseMine(indexToLocation(i), GameConstants.SQ_CHEESE_SPAWN_RADIUS, null);
                this.cheeseMines.add(newMine);
                cheeseMineLocs[i] = newMine;
            }
        }

        for (CheeseMine mine : this.cheeseMines) {
            MapLocation symLoc = symmetryLocation(mine.getLocation());
            mine.setPair(cheeseMineLocs[locationToIndex(symLoc)]);
        }

        this.sharedArray = new int[2][GameConstants.SHARED_ARRAY_SIZE];

        RobotInfo[] initialBodies = gm.getInitialBodies();

        for (int i = 0; i < initialBodies.length; i++) {
            RobotInfo robotInfo = initialBodies[i];
            MapLocation newLocation = robotInfo.location.translate(gm.getOrigin().x, gm.getOrigin().y);
            spawnRobot(robotInfo.ID, robotInfo.type, newLocation, robotInfo.direction, robotInfo.chirality,
                    robotInfo.team);
        }

        // cat bfs map
        this.bfs_map_0 = new Direction[width*height][width*height];
        this.bfs_map_1 = new Direction[width*height][width*height];
        
        for (int target_x=0; target_x < width; target_x++){
            for (int target_y=0; target_y < height; target_y++){
                MapLocation source = new MapLocation(target_x, target_y);
                bfsFromTarget(source, 0);
                bfsFromTarget(source, 1);
            }
        }


    }

    public void bfsFromTarget(MapLocation target, int chirality){
        // bfs form target to all possible sources, set source direction to target

        Direction[][] bfs_map;
        
        if (chirality == 0)
            bfs_map = this.bfs_map_0;
        else
            bfs_map = this.bfs_map_1;

        Queue<MapLocation> queue = new LinkedList<MapLocation>();
        queue.add(target);

        bfs_map[locationToIndex(target)][locationToIndex(target)] = Direction.CENTER;

        while(!queue.isEmpty()){
            MapLocation nextLoc = queue.poll();
            
            // check neighbors
            for (Direction d : Direction.allDirections()){
                if (d == Direction.CENTER)
                    continue;

                Direction useDir;
                if (chirality == 1){
                    useDir = flipDirBySymmetry(d);
                }
                else{
                    useDir = d;
                }
                
                MapLocation neighbor = nextLoc.add(useDir);

                if (this.gameMap.onTheMap(neighbor) && bfs_map[locationToIndex(neighbor)][locationToIndex(target)] != null){
                    // visited already
                    continue;
                }

                // check this path works for all cat locations
                boolean validPath = true;
                
                Direction[] dirsFromCenterLoc;

                if (chirality == 0) {
                    dirsFromCenterLoc = new Direction[]{Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST};
                } else {
                    dirsFromCenterLoc = new Direction[]{flipDirBySymmetry(Direction.CENTER), flipDirBySymmetry(Direction.NORTH), flipDirBySymmetry(Direction.NORTHEAST), flipDirBySymmetry(Direction.EAST)};
                }

                for (Direction dirFromCenter : dirsFromCenterLoc){
                    MapLocation neighborCorner = neighbor.add(dirFromCenter);
                    boolean onTheMap = this.gameMap.onTheMap(neighborCorner);

                    if (!onTheMap || this.getWall(neighborCorner)){
                        // location not on map or has walls
                        validPath = false;
                        break;
                    }
                }

                if (validPath) {
                    Direction reverseDirection = useDir.opposite();
                    bfs_map[locationToIndex(neighbor)][locationToIndex(target)] = reverseDirection;
                    queue.add(neighbor);
                }
            }   
        }
    }

    public Direction getBfsDir(MapLocation from, MapLocation to, int chirality){
        if (chirality==0)
            return bfs_map_0[locationToIndex(from)][locationToIndex(to)];
        else
            return bfs_map_1[locationToIndex(from)][locationToIndex(to)];
    }

    /**
     * Run a single round of the game.
     *
     * @return the state of the game after the round has run
     */
    public synchronized GameState runRound() {
        if (!this.isRunning()) {
            List<ProfilerCollection> profilers = new ArrayList<>(2);
            if (!profilerCollections.isEmpty()) {
                profilers.add(profilerCollections.get(Team.A));
                profilers.add(profilerCollections.get(Team.B));
            }

            // Write match footer if game is done
            matchMaker.makeMatchFooter(gameStats.getWinner(), gameStats.getDominationFactor(), currentRound, profilers);
            return GameState.DONE;
        }

        try {
            this.processBeginningOfRound();
            this.controlProvider.roundStarted();

            updateDynamicBodies();

            this.controlProvider.roundEnded();
            this.processEndOfRound();

            if (!this.isRunning()) {
                this.controlProvider.matchEnded();
            }
        } catch (Exception e) {
            ErrorReporter.report(e);
            return GameState.DONE;
        }

        return GameState.RUNNING;
    }

    private void updateDynamicBodies() {
        objectInfo.eachDynamicBodyByExecOrder((body) -> {
            if (body instanceof InternalRobot) {
                return updateRobot((InternalRobot) body);
            } else {
                throw new RuntimeException("non-robot body registered as dynamic");
            }
        });
    }

    private boolean updateRobot(InternalRobot robot) {
        robot.processBeginningOfTurn();
        this.controlProvider.runRobot(robot);
        robot.setBytecodesUsed(this.controlProvider.getBytecodesUsed(robot));
        robot.processEndOfTurn();

        // If the robot terminates but the death signal has not yet
        // been visited:

        if (this.controlProvider.getTerminated(robot) && objectInfo.getRobotByID(robot.getID()) != null
                && robot.getLocation() != null) {
            destroyRobot(robot.getID());
        }

        return true;
    }

    // *********************************
    // ****** BASIC MAP METHODS ********
    // *********************************

    public int getMapSeed() {
        return this.gameMap.getSeed();
    }

    public LiveMap getGameMap() {
        return this.gameMap;
    }

    public TeamInfo getTeamInfo() {
        return this.teamInfo;
    }

    public GameStats getGameStats() {
        return this.gameStats;
    }

    public ObjectInfo getObjectInfo() {
        return this.objectInfo;
    }

    public GameMaker.MatchMaker getMatchMaker() {
        return this.matchMaker;
    }

    public Team getWinner() {
        return this.gameStats.getWinner();
    }

    public boolean isRunning() {
        return this.running;
    }

    public int getCurrentRound() {
        return this.currentRound;
    }

    public boolean isCooperation() {
        return this.isCooperation;
    }

    public boolean getWall(MapLocation loc) {
        return this.walls[locationToIndex(loc)];
    }

    public boolean getDirt(MapLocation loc) {
        return this.dirt[locationToIndex(loc)];
    }

    public int getCheese(MapLocation loc) {
        return this.cheeseAmounts[locationToIndex(loc)];
    }

    /**
     * Allows a robot to add or remove dirt (add = true, remove = false)
     * to a location on the map
     * 
     * @param loc, the location in MapLocation to add/remove dirt
     * @param val, true if adding dirt, false if removing dirt
     * 
     * @returns void, modifies GameWorld's dirt array in place
     */
    public void setDirt(MapLocation loc, boolean val) {
        if (loc == null)
            return;
        int mapIndex = locationToIndex(loc);
        this.dirt[mapIndex] = val;

    }

    public int getCheeseAmount(MapLocation loc) {
        return this.cheeseAmounts[locationToIndex(loc)];
    }

    public void removeCheese(MapLocation loc) {
        this.cheeseAmounts[locationToIndex(loc)] = 0;
    }

    public void addCheese(MapLocation loc, int amount) {
        this.cheeseAmounts[locationToIndex(loc)] += amount;
    }

    public int getNumCats() {
        return this.numCats;
    }

    public boolean isPassable(MapLocation loc) {
        return !(this.walls[locationToIndex(loc)]
                || this.dirt[locationToIndex(loc)]);
    }

    /**
     * Helper method that converts a location into an index.
     * 
     * @param loc the MapLocation
     */
    public int locationToIndex(MapLocation loc) {
        return this.gameMap.locationToIndex(loc);
    }

    /**
     * Helper method that converts an index into a location.
     * 
     * @param idx the index
     */
    public MapLocation indexToLocation(int idx) {
        return gameMap.indexToLocation(idx);
    }

    // ***********************************
    // ****** CHEESE METHODS *************
    // ***********************************

    public void spawnCheese(CheeseMine mine) {
        boolean spawn = rand.nextFloat() < mine.generationProbability(currentRound);

        if (spawn) {
            int dx = rand.nextInt(-GameConstants.SQ_CHEESE_SPAWN_RADIUS, GameConstants.SQ_CHEESE_SPAWN_RADIUS);
            int dy = rand.nextInt(-GameConstants.SQ_CHEESE_SPAWN_RADIUS, GameConstants.SQ_CHEESE_SPAWN_RADIUS);

            MapLocation ogSpawnLoc = mine.getLocation();
            MapLocation pairedSpawnLoc = mine.getPair().getLocation();
            CheeseMine pairedMine = mine.getPair();

            for (int invalidSpawns = 0; invalidSpawns < 5; invalidSpawns++) {
                int pair_dx = gameMap.getSymmetry() == MapSymmetry.VERTICAL ? dx : -dx;
                int pair_dy = gameMap.getSymmetry() == MapSymmetry.HORIZONTAL ? dy : -dy;

                int cheeseX = mine.getLocation().x + dx;
                int cheeseY = mine.getLocation().y + dy;

                int pairedX = pairedMine.getLocation().x + pair_dx;
                int pairedY = pairedMine.getLocation().y + pair_dy;

                // check cheeseX and cheeseY is on map
                if (cheeseX >= 0 && cheeseX < this.gameMap.getWidth() && cheeseY >= 0
                        && cheeseY < this.gameMap.getHeight()
                        && pairedX >= 0 && pairedX < this.gameMap.getWidth() && pairedY >= 0
                        && pairedY < this.gameMap.getHeight()
                        && !this.getWall(new MapLocation(cheeseX, cheeseY))
                        && !this.getWall(new MapLocation(pairedX, pairedY))) {
                    ogSpawnLoc = new MapLocation(cheeseX, cheeseY);
                    pairedSpawnLoc = new MapLocation(pairedX, pairedY);
                    break;
                }

            }

            // if rotational, flip both symmetries, if vertical/horizontal, only flip the
            // corresponding one

            if (spawn) {

                mine.setLastRound(this.currentRound);
                pairedMine.setLastRound(this.currentRound);

                matchMaker.addCheeseSpawnAction(ogSpawnLoc, GameConstants.CHEESE_SPAWN_AMOUNT + this.getCheeseAmount(ogSpawnLoc));
                matchMaker.addCheeseSpawnAction(pairedSpawnLoc, GameConstants.CHEESE_SPAWN_AMOUNT + this.getCheeseAmount(pairedSpawnLoc));

                addCheese(ogSpawnLoc, GameConstants.CHEESE_SPAWN_AMOUNT);
                addCheese(pairedSpawnLoc, GameConstants.CHEESE_SPAWN_AMOUNT);
            }

        }
    }

    public boolean hasCheeseMine(MapLocation loc) {
        return this.cheeseMineLocs[locationToIndex(loc)] != null;
    }

    // ***********************************
    // ****** TRAP METHODS **************
    // ***********************************

    public Trap getTrap(MapLocation loc) {
        return this.trapLocations[locationToIndex(loc)];
    }

    public boolean hasTrap(MapLocation loc) {
        return (this.trapLocations[locationToIndex(loc)] != null);
    }

    public boolean hasRatTrap(MapLocation loc) {
        Trap trap = this.trapLocations[locationToIndex(loc)];
        return (trap != null && trap.getType() == TrapType.RAT_TRAP);
    }

    public boolean hasCatTrap(MapLocation loc) {
        Trap trap = this.trapLocations[locationToIndex(loc)];
        return (trap != null && trap.getType() == TrapType.CAT_TRAP);
    }

    public ArrayList<Trap> getTrapTriggers(MapLocation loc) {
        return this.trapTriggers[locationToIndex(loc)];
    }

    public void placeTrap(MapLocation loc, Trap trap) {

        TrapType type = trap.getType();
        Team team = trap.getTeam();

        int idx = locationToIndex(loc);
        this.trapLocations[idx] = trap;

        for (MapLocation adjLoc : getAllLocationsWithinRadiusSquared(loc, type.triggerRadiusSquared, 0)) {// set chirality to 0, only rats will be placing traps
            this.trapTriggers[locationToIndex(adjLoc)].add(trap);
        }

        int[] trapTypeCounts = this.trapCounts.get(type);
        trapTypeCounts[team.ordinal()] += 1;
        this.trapCounts.put(type, trapTypeCounts);
    }

    public void removeTrap(MapLocation loc) {
        Trap trap = this.trapLocations[locationToIndex(loc)];
        if (trap == null) {
            return;
        }
        TrapType type = trap.getType();
        Team team = trap.getTeam();
        int[] trapTypeCounts = this.trapCounts.get(type);
        trapTypeCounts[team.ordinal()] -= 1;
        this.trapCounts.put(type, trapTypeCounts);
        this.trapLocations[locationToIndex(loc)] = null;

        for (MapLocation adjLoc : getAllLocationsWithinRadiusSquared(loc, type.triggerRadiusSquared, 0)) { // set chirality to 0, only rats will be removing traps
            this.trapTriggers[locationToIndex(adjLoc)].remove(trap);
        }
    }

    public int getTrapCount(TrapType type, Team team) {
        return this.trapCounts.get(type)[team.ordinal()];
    }

    public void triggerTrap(Trap trap, InternalRobot robot) {
        // will only be called for matching trap and robot types
        Team triggeringTeam = robot.getTeam();
        MapLocation loc = trap.getLocation();
        TrapType type = trap.getType();

        robot.setMovementCooldownTurns(type.stunTime);
        if (type == TrapType.CAT_TRAP && robot.getType().isCatType()) {
            this.teamInfo.addDamageToCats(trap.getTeam(), type.damage);
        }

        if (trap.getType() != TrapType.CAT_TRAP) {
            // initiate backstab
            this.isCooperation = false;
        }

        matchMaker.addTrapTriggerAction(trap.getId(), loc, triggeringTeam, type);

        removeTrap(loc);
        robot.addHealth(-type.damage);
        // matchMaker.addAction(robot.getID(),
        // FlatHelpers.getTrapActionFromTrapType(type),
        // locationToIndex(trap.getLocation()));
    }

    // ***********************************
    // ****** ROBOT METHODS **************
    // ***********************************

    public InternalRobot getRobot(MapLocation loc) {
        return this.robots[loc.x - this.gameMap.getOrigin().x][loc.y - this.gameMap.getOrigin().y];
    }

    public void moveRobot(MapLocation start, MapLocation end) {
        addRobot(end, getRobot(start));
        removeRobot(start);
    }

    public void addRobot(MapLocation loc, InternalRobot robot) {
        this.robots[loc.x - this.gameMap.getOrigin().x][loc.y - this.gameMap.getOrigin().y] = robot;
    }

    public void removeRobot(MapLocation loc) {
        this.robots[loc.x - this.gameMap.getOrigin().x][loc.y - this.gameMap.getOrigin().y] = null;
    }

    public InternalRobot[] getAllRobotsWithinRadiusSquared(MapLocation center, int radiusSquared, int chirality) {
        return getAllRobotsWithinRadiusSquared(center, radiusSquared, null, chirality);
    }

    public InternalRobot[] getAllRobotsWithinRadiusSquared(MapLocation center, int radiusSquared, Team team, int chirality) {
        ArrayList<InternalRobot> returnRobots = new ArrayList<InternalRobot>();
        for (MapLocation newLocation : getAllLocationsWithinRadiusSquared(center, radiusSquared, chirality))
            if (getRobot(newLocation) != null) {
                if (team == null || getRobot(newLocation).getTeam() == team)
                    returnRobots.add(getRobot(newLocation));
            }
        return returnRobots.toArray(new InternalRobot[returnRobots.size()]);
    }

    public InternalRobot[] getAllRobotsWithinConeRadiusSquared(MapLocation center, Direction lookDirection,
            double totalAngle, int radiusSquared, int chirality) {
        return getAllRobotsWithinConeRadiusSquared(center, lookDirection, totalAngle, radiusSquared, null, chirality);
    }

    public InternalRobot[] getAllRobotsWithinConeRadiusSquared(MapLocation center, Direction lookDirection,
            double totalAngle, int radiusSquared, Team team, int chirality) {
        ArrayList<InternalRobot> returnRobots = new ArrayList<InternalRobot>();
        for (MapLocation newLocation : getAllLocationsWithinConeRadiusSquared(center, lookDirection, totalAngle,
                radiusSquared, chirality))
            if (getRobot(newLocation) != null) {
                if (team == null || getRobot(newLocation).getTeam() == team)
                    returnRobots.add(getRobot(newLocation));
            }
        return returnRobots.toArray(new InternalRobot[returnRobots.size()]);
    }

    public InternalRobot[] getAllRobots(Team team, int chirality) {
        ArrayList<InternalRobot> returnRobots = new ArrayList<InternalRobot>();
        for (MapLocation newLocation : getAllLocations(chirality)) {
            if (getRobot(newLocation) != null && (team == null || getRobot(newLocation).getTeam() == team)) {
                returnRobots.add(getRobot(newLocation));
            }
        }
        return returnRobots.toArray(new InternalRobot[returnRobots.size()]);
    }

    public MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared, int chirality) {
        return getAllLocationsWithinConeRadiusSquaredWithoutMap(
                this.gameMap.getOrigin(),
                this.gameMap.getWidth(),
                this.gameMap.getHeight(),
                center, Direction.CENTER, 360, radiusSquared, chirality);
    }

    public MapLocation[] getAllLocationsWithinConeRadiusSquared(MapLocation center, Direction lookDirection,
            double totalAngle, int radiusSquared, int chirality) {
        return getAllLocationsWithinConeRadiusSquaredWithoutMap(
                this.gameMap.getOrigin(),
                this.gameMap.getWidth(),
                this.gameMap.getHeight(),
                center,
                lookDirection,
                totalAngle, radiusSquared, chirality);
    }

    public MapLocation[] getAllLocationsWithinConeRadiusSquaredWithoutMap(MapLocation origin,
            int width, int height,
            MapLocation center,
            Direction lookDirection,
            double angle, int radiusSquared, int chirality) {
        ArrayList<MapLocation> returnLocations = new ArrayList<MapLocation>();
        int ceiledRadius = (int) Math.ceil(Math.sqrt(radiusSquared)) + 1; // add +1 just to be safe
        int minX = Math.max(center.x - ceiledRadius, origin.x);
        int minY = Math.max(center.y - ceiledRadius, origin.y);
        int maxX = Math.min(center.x + ceiledRadius, origin.x + width - 1);
        int maxY = Math.min(center.y + ceiledRadius, origin.y + height - 1);

        ArrayList<Integer> x_list = new ArrayList<>();
        ArrayList<Integer> y_list = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            x_list.add(x);
        }
        for (int y = minY; y <= maxY; y++) {
            y_list.add(y);
        }

        if (chirality == 1){
            MapSymmetry symmetry = this.getGameMap().getSymmetry();
            switch (symmetry){
                case HORIZONTAL:
                    Collections.reverse(y_list);
                    break;
                case VERTICAL:
                    Collections.reverse(x_list);
                    break;
                case ROTATIONAL:
                    Collections.reverse(x_list);
                    Collections.reverse(y_list);
                    break;
            }
        }

        for (int x : x_list) {
            for (int y : y_list) {
                MapLocation newLocation = new MapLocation(x, y);

                if (center.isWithinDistanceSquared(newLocation, radiusSquared, lookDirection, angle)) {
                    returnLocations.add(newLocation);
                }
            }
        }
        return returnLocations.toArray(new MapLocation[returnLocations.size()]);
    }


    public void addHasTraveledRobot(int id){
        this.hasTraveledIDs.add(id);
    }
    public boolean getHasTraveledRobot(int id){
        return this.hasTraveledIDs.contains(id);
    }

    /**
     * @return all of the locations on the grid
     */
    private MapLocation[] getAllLocations(int chirality) {
        return getAllLocationsWithinRadiusSquared(new MapLocation(0, 0), Integer.MAX_VALUE, chirality);
    }

    // *********************************
    // ****** GAMEPLAY *****************
    // *********************************

    public void processBeginningOfRound() {
        currentRound++;

        this.getMatchMaker().startRound(currentRound);
        // Process beginning of each robot's round
        objectInfo.eachRobot((robot) -> {
            robot.processBeginningOfRound();
            return true;
        });
    }

    public void setWinner(Team t, DominationFactor d) {
        gameStats.setWinner(t);
        gameStats.setDominationFactor(d);
    }

    /**
     * @return whether a team's rat kings are all dead
     */
    public boolean setWinnerIfKilledAllRatKings() {
        // all rat kings dead

        if (this.getTeamInfo().getNumRatKings(Team.A) == 0) {
            gameStats.setWinner(Team.B);
            gameStats.setDominationFactor(DominationFactor.KILL_ALL_RAT_KINGS);
            return true;
        } else if (this.getTeamInfo().getNumRatKings(Team.B) == 0) {
            gameStats.setWinner(Team.A);
            gameStats.setDominationFactor(DominationFactor.KILL_ALL_RAT_KINGS);
            return true;
        }

        return false;
    }

    /**
     * @return whether all cats dead
     */
    public boolean setWinnerifAllCatsDead() {
        if (this.getNumCats() == 0 && this.isCooperation) { // only end game if no more cats in cooperation mode
            // find out which team won via points
            if (setWinnerIfMorePoints())
                return true;
            if (setWinnerIfMoreCheese())
                return true;
            if (setWinnerIfMoreRatsAlive())
                return true;
            setWinnerArbitrary();

            return true;
        }
        return false;

    }

    /**
     * @return whether a team has more cheese
     */
    public boolean setWinnerIfMoreCheese() {
        int[] totalCheeseValues = new int[2];

        // consider team reserves
        totalCheeseValues[Team.A.ordinal()] += this.teamInfo.getCheese(Team.A);
        totalCheeseValues[Team.B.ordinal()] += this.teamInfo.getCheese(Team.B);

        if (totalCheeseValues[Team.A.ordinal()] > totalCheeseValues[Team.B.ordinal()]) {
            setWinner(Team.A, DominationFactor.MORE_CHEESE);
            return true;
        } else if (totalCheeseValues[Team.B.ordinal()] > totalCheeseValues[Team.A.ordinal()]) {
            setWinner(Team.B, DominationFactor.MORE_CHEESE);
            return true;
        }
        return false;
    }

    /**
     * @return whether a team has more allied rats alive
     */
    public boolean setWinnerIfMoreRatsAlive() {
        int[] totalRobotsAlive = new int[2];

        totalRobotsAlive[0] = this.getTeamInfo().getNumBabyRats(Team.A) + this.getTeamInfo().getNumRatKings(Team.A);
        totalRobotsAlive[1] = this.getTeamInfo().getNumBabyRats(Team.B) + this.getTeamInfo().getNumRatKings(Team.B);

        if (totalRobotsAlive[Team.A.ordinal()] > totalRobotsAlive[Team.B.ordinal()]) {
            setWinner(Team.A, DominationFactor.MORE_ROBOTS_ALIVE);
            return true;
        } else if (totalRobotsAlive[Team.B.ordinal()] > totalRobotsAlive[Team.A.ordinal()]) {
            setWinner(Team.B, DominationFactor.MORE_ROBOTS_ALIVE);
            return true;
        }
        return false;
    }

    /**
     * @return whether a team has more points depending on the game state
     */
    public boolean setWinnerIfMorePoints() {
        double cat_weight; // cat damage
        double king_weight; // number of kings
        double cheese_transfer_weight; // amount cheese transferred

        if (isCooperation()) {
            cat_weight = 0.5;
            king_weight = 0.3;
            cheese_transfer_weight = 0.2;
        } else {
            cat_weight = 0.3;
            king_weight = 0.5;
            cheese_transfer_weight = 0.2;
        }

        ArrayList<Integer> teamPoints = new ArrayList<>();

        int total_num_rat_kings = teamInfo.getNumRatKings(Team.A) + teamInfo.getNumRatKings(Team.B);
        int total_amount_cheese_transferred = teamInfo.getCheeseTransferred(Team.A) + teamInfo.getCheeseTransferred(Team.B);
        int total_amount_cat_damage = teamInfo.getDamageToCats(Team.A) + teamInfo.getDamageToCats(Team.B);

        for (Team team : List.of(Team.A, Team.B)) {

            float proportion_rat_kings = total_num_rat_kings != 0 ? (float)teamInfo.getNumRatKings(team) / total_num_rat_kings : 0.0f; 
            float proportion_cheese_transferred = total_amount_cheese_transferred != 0 ? (float)teamInfo.getCheeseTransferred(team) / total_amount_cheese_transferred : 0.0f;
            float proportion_cat_damage = total_amount_cat_damage != 0 ? (float)teamInfo.getDamageToCats(team) / total_amount_cat_damage : 0.0f;

            int points = (int) (cat_weight * 100 * proportion_cat_damage + king_weight * 100 * proportion_rat_kings
                    + cheese_transfer_weight * 100 * proportion_cheese_transferred);
            this.teamInfo.addPoints(team, points);
            teamPoints.add(points);
        }
        if (teamPoints.getFirst() > teamPoints.getLast()) {
            setWinner(Team.A, DominationFactor.MORE_POINTS);
            return true;
        } else if (teamPoints.getFirst() < teamPoints.getLast()) {
            setWinner(Team.B, DominationFactor.MORE_POINTS);
            return true;
        }

        return false;
    }

    /**
     * Sets a winner arbitrarily. Hopefully this is actually random.
     */
    public void setWinnerArbitrary() {
        setWinner(Math.random() < 0.5 ? Team.A : Team.B, DominationFactor.WON_BY_DUBIOUS_REASONS);
    }

    public boolean timeLimitReached() {
        return currentRound >= this.gameMap.getRounds();
    }

    /**
     * Checks end of match and then decides winner based on tiebreak conditions
     */
    public void checkEndOfMatch() {
        if (timeLimitReached() && gameStats.getWinner() == null) {
            if (setWinnerIfMorePoints())
                return;
            if (setWinnerIfMoreCheese())
                return;
            if (setWinnerIfMoreRatsAlive())
                return;
            setWinnerArbitrary();
        }
    }

    private void checkWin(Team team) {
        
        // killed all of both team's rat kings in the same round
        if (gameStats.getWinner() != null && gameStats.getDominationFactor() == DominationFactor.KILL_ALL_RAT_KINGS && setWinnerIfKilledAllRatKings()){
            if (setWinnerIfMorePoints())
                return;
            if (setWinnerIfMoreCheese())
                return;
            if (setWinnerIfMoreRatsAlive())
                return;
            setWinnerArbitrary();
        }
        if (gameStats.getWinner() != null) { // to avoid overriding previously set win
            return;
        }
        if (setWinnerIfKilledAllRatKings()){
            return;
        }
        // all cats dead
        if (setWinnerifAllCatsDead()) {
            return;
        }

        throw new InternalError("Reporting incorrect win");
    }

    public void processEndOfRound() {
        // clear hasRunCheeseMinesThisRound for next round
        this.hasRunCheeseMinesThisRound =  false;

        Team[] teams = {Team.A, Team.B};
        for (Team t : teams){
            // combine total cheese into the rat kings stat
            int combined_stat = this.teamInfo.getNumRatKings(t) + 10*this.teamInfo.getCheese(t);
            this.matchMaker.addTeamInfo(t, this.teamInfo.getCheeseTransferred(t), this.teamInfo.getDamageToCats(t), combined_stat, this.teamInfo.getNumBabyRats(t), this.teamInfo.getDirt(t), this.getTrapCount(TrapType.RAT_TRAP, t), this.getTrapCount(TrapType.CAT_TRAP, t));
        }
        this.teamInfo.processEndOfRound();
        hasTraveledIDs.clear();

        this.getMatchMaker().endRound();

        checkEndOfMatch();

        if (gameStats.getWinner() != null)
            running = false;
    }

    // *********************************
    // ****** SPAWNING *****************
    // *********************************

    public int spawnRobot(int ID, UnitType type, MapLocation location, Direction dir, int chirality, Team team) {
        // if direction is CENTER, the robot doesn't have a preset direction; set the
        // robot to face the middle of the map
        // subtract 1 before dividing since cats use bottom left corner as center so we
        // will use the bottom left corner of the center 2x2 of the map as the point of
        // comparison

        if (dir == Direction.CENTER) {
            MapLocation mapCenter = new MapLocation((this.getGameMap().getWidth() - 1) / 2,
                    (this.getGameMap().getHeight() - 1) / 2);
            dir = location.directionTo(mapCenter);
        }

        InternalRobot robot = new InternalRobot(this, ID, team, type, location, dir, chirality);

        for (MapLocation loc : robot.getAllPartLocations()) {
            addRobot(loc, robot);
        }

        objectInfo.createRobot(robot);
        controlProvider.robotSpawned(robot);

        if (type.isBabyRatType()) {
            this.teamInfo.addBabyRats(1, team);
        } else if (type.isRatKingType()) {
            this.teamInfo.addRatKings(1, team);
        } else if (type.isCatType()) {
            this.numCats += 1;
        }

        if (!type.isCatType()) {
            this.currentNumberUnits[team.ordinal()] += 1;
        }
        return ID;
    }

    public int spawnRobot(UnitType type, MapLocation location, Direction dir, int chirality, Team team) {
        int ID = idGenerator.nextID();

        return spawnRobot(ID, type, location, dir, chirality, team);
    }

    public void squeak(InternalRobot robot, Message message) {
        MapLocation robotLoc = robot.getLocation();
        MapLocation[] locations = getAllLocationsWithinRadiusSquared(robotLoc, GameConstants.SQUEAK_RADIUS_SQUARED, 0); // chirality doesn't matter here

        for (MapLocation loc : locations) {
            InternalRobot otherRobot = getRobot(loc);

            if (otherRobot != null && (otherRobot.getType().isCatType() || otherRobot.getTeam() == robot.getTeam())) {
                otherRobot.addMessage(message.copy());
            }
        }

        matchMaker.addSqueakAction(robotLoc);
    }

    public void writeSharedArray(int index, int value, Team team) {
        this.sharedArray[team.ordinal()][index] = value;
    }

    public int readSharedArray(int index, Team team) {
        return this.sharedArray[team.ordinal()][index];
    }

    public void writePersistentArray(int index, int value, Team team) {
        this.persistentArray[team.ordinal()][index] = value;
    }

    public int readPersistentArray(int index, Team team) {
        return this.persistentArray[team.ordinal()][index];
    }

    // *********************************
    // ****** DESTROYING ***************
    // *********************************

    /**
     * Permanently destroy a robot
     */
    public void destroyRobot(int id) {
        destroyRobot(id, false, false);
    }

    public void updateCatHealth(int id, int newHealth) {
        objectInfo.updateCatHealth(id, newHealth);
    }

    public void destroyRobot(int id, boolean fromException, boolean fromDamage) {
        InternalRobot robot = objectInfo.getRobotByID(id);
        if (robot == null) {
            // robot was already killed
            return;
        }

        Team robotTeam = robot.getTeam();
        MapLocation loc = robot.getLocation();

        if (loc != null) {
            if (robot.getType().isBabyRatType()) {
                this.teamInfo.addBabyRats(-1, robotTeam);
            } else if (robot.getType().isRatKingType()) {
                this.teamInfo.addRatKings(-1, robotTeam);
            } else if (robot.getType().isCatType()) {
                this.numCats -= 1;
            }

            for (MapLocation robotLoc : robot.getAllPartLocations()) {
                removeRobot(robotLoc);
            }

            if (robot.isCarryingRobot()) {
                InternalRobot carryingRobot = robot.getRobotBeingCarried();
                carryingRobot.getDropped(loc);
            }

            if (robot.isGrabbedByRobot()) {
                InternalRobot carrier = robot.getGrabbedByRobot();
                robot.clearGrabbedByRobot();

                if (carrier != null && carrier.getRobotBeingCarried() == robot) {
                    carrier.clearCarryingRobot();
                }
            }

            if (robot.getCheese() > 0) {
                addCheese(loc, robot.getCheese());
                matchMaker.addCheeseSpawnAction(loc, robot.getCheese());
            }
        }

        controlProvider.robotKilled(robot);
        objectInfo.destroyRobot(id);

        if (fromDamage || fromException) {
            matchMaker.addDieAction(id, fromException);
        } else {
            matchMaker.addDied(id);
        }

        if (robot.getType() != UnitType.CAT) {
            this.currentNumberUnits[robot.getTeam().ordinal()] -= 1;
        }

        // check win
        if (robot.getType() == UnitType.RAT_KING && this.getTeamInfo().getNumRatKings(robot.getTeam()) == 0) {
            checkWin(robotTeam);
        } else if (this.isCooperation && robot.getType() == UnitType.CAT && this.getNumCats() == 0) {
            checkWin(robotTeam);
        }
    }

    // *********************************
    // ********* PROFILER **************
    // *********************************

    public void setProfilerCollection(Team team, ProfilerCollection profilerCollection) {
        if (profilerCollections == null) {
            profilerCollections = new HashMap<>();
        }
        
        profilerCollections.put(team, profilerCollection);
    }
}
