package battlecode.common;

/**
 * A RobotController allows contestants to make their robot sense and interact
 * with the game world. When a contestant's <code>RobotPlayer</code> is
 * constructed, it is passed an instance of <code>RobotController</code> that
 * controls the newly created robot.
 */
@SuppressWarnings("unused")
public interface RobotController {

    // *********************************
    // ****** GLOBAL QUERY METHODS *****
    // *********************************

    /**
     * Returns the current round number, where round 1 is the first round of the
     * match.
     *
     * @return the current round number, where round 1 is the first round of the
     *         match
     *
     * @battlecode.doc.costlymethod
     */
    int getRoundNum();

    /**
     * Returns the width of the game map. Valid x coordinates range from
     * 0 (inclusive) to the width (exclusive).
     *
     * @return the map width
     *
     * @battlecode.doc.costlymethod
     */
    int getMapWidth();

    /**
     * Returns the height of the game map. Valid y coordinates range from
     * 0 (inclusive) to the height (exclusive).
     *
     * @return the map height
     *
     * @battlecode.doc.costlymethod
     */
    int getMapHeight();

    // *********************************
    // ****** UNIT QUERY METHODS *******
    // *********************************

    /**
     * Returns the ID of this robot.
     *
     * @return the ID of this robot
     *
     * @battlecode.doc.costlymethod
     */
    int getID();

    /**
     * Returns this robot's Team.
     *
     * @return this robot's Team
     *
     * @battlecode.doc.costlymethod
     */
    Team getTeam();

    /**
     * Returns this robot's current location.
     *
     * @return this robot's current location
     *
     * @battlecode.doc.costlymethod
     */
    MapLocation getLocation();

    /**
     * Returns this robot's current direction.
     *
     * @return this robot's current direction
     *
     * @battlecode.doc.costlymethod
     */
    Direction getDirection();

    /**
     * Returns this robot's current health.
     *
     * @return this robot's current health
     *
     * @battlecode.doc.costlymethod
     */
    int getHealth();

    /**
     * Returns the amount of cheese the robot is currently holding.
     *
     * @return the amount of cheese the robot is currently holding.
     *
     * @battlecode.doc.costlymethod
     */
    int getRawCheese();

    /**
     * Returns the amount of global cheese available.
     *
     * @return the amount of global cheese available.
     *
     * @battlecode.doc.costlymethod
     */
    public int getGlobalCheese();

    /**
     * Returns the amount of cheese the robot has access to.
     *
     * @return the amount of cheese the robot has access to.
     *
     * @battlecode.doc.costlymethod
     */
    public int getAllCheese();

    /**
     * Returns the amount of dirt that this robot's team has.
     * 
     * @return the amount of dirt this robot's team has
     * 
     * @battlecode.doc.costlymethod
     */
    int getDirt();

    /**
     * Returns what UnitType this robot is.
     * 
     * @return the UnitType of this robot
     * 
     * @battlecode.doc.costlymethod
     */
    UnitType getType();

    // ***********************************
    // ****** GENERAL VISION METHODS *****
    // ***********************************

    /**
     * Checks whether a MapLocation is on the map.
     *
     * @param loc the location to check
     * @return true if the location is on the map; false otherwise
     *
     * @battlecode.doc.costlymethod
     */
    boolean onTheMap(MapLocation loc);

    /**
     * Checks whether the given location is within the robot's vision range, and if
     * it is on the map.
     *
     * @param loc the location to check
     * @return true if the given location is within the robot's vision range and is
     *         on the map; false otherwise
     *
     * @battlecode.doc.costlymethod
     */
    boolean canSenseLocation(MapLocation loc);

    /**
     * Checks whether a robot is at a given location. Assumes the location is valid.
     *
     * @param loc the location to check
     * @return true if a robot is at the location
     * @throws GameActionException if the location is not within vision range or on
     *                             the map
     *
     * @battlecode.doc.costlymethod
     */
    boolean isLocationOccupied(MapLocation loc) throws GameActionException;

    /**
     * Checks whether a robot is at a given location. Assume the location is valid.
     *
     * @param loc the location to check
     * @return true if a robot is at the location, false if there is no robot or the
     *         location can not be sensed
     *
     * @battlecode.doc.costlymethod
     */
    boolean canSenseRobotAtLocation(MapLocation loc);

    /**
     * Senses the robot at the given location, or null if there is no robot
     * there.
     *
     * @param loc the location to check
     * @return the robot at the given location
     * @throws GameActionException if the location is not within vision range
     *
     * @battlecode.doc.costlymethod
     */
    RobotInfo senseRobotAtLocation(MapLocation loc) throws GameActionException;

    /**
     * Tests whether the given robot exists and if it is within this robot's
     * vision range.
     *
     * @param id the ID of the robot to query
     * @return true if the given robot is within this robot's vision range and
     *         exists;
     *         false otherwise
     *
     * @battlecode.doc.costlymethod
     */
    boolean canSenseRobot(int id);

    /**
     * Senses information about a particular robot given its ID.
     *
     * @param id the ID of the robot to query
     * @return a RobotInfo object for the sensed robot
     * @throws GameActionException if the robot cannot be sensed (for example,
     *                             if it doesn't exist or is out of vision range)
     *
     * @battlecode.doc.costlymethod
     */
    RobotInfo senseRobot(int id) throws GameActionException;

    /**
     * Returns all robots within vision radius. The objects are returned in no
     * particular order.
     *
     * @return array of RobotInfo objects, which contain information about all
     *         the robots you saw
     *
     * @battlecode.doc.costlymethod
     */
    RobotInfo[] senseNearbyRobots();

    /**
     * Returns all robots that can be sensed within a certain distance of this
     * robot. The objects are returned in no particular order.
     *
     * @param radiusSquared return robots this distance away from the center of
     *                      this robot; if -1 is passed, all robots within vision
     *                      radius are returned;
     *                      if radiusSquared is larger than the robot's vision
     *                      radius, the vision
     *                      radius is used
     * @return array of RobotInfo objects of all the robots you saw
     * @throws GameActionException if the radius is negative (and not -1)
     *
     * @battlecode.doc.costlymethod
     */
    RobotInfo[] senseNearbyRobots(int radiusSquared) throws GameActionException;

    /**
     * Returns all robots of a given team that can be sensed within a certain
     * distance of this robot. The objects are returned in no particular order.
     *
     * @param radiusSquared return robots this distance away from the center of
     *                      this robot; if -1 is passed, all robots within vision
     *                      radius are returned;
     *                      if radiusSquared is larger than the robot's vision
     *                      radius, the vision
     *                      radius is used
     * @param team          filter game objects by the given team; if null is
     *                      passed,
     *                      robots from any team are returned
     * @return array of RobotInfo objects of all the robots you saw
     * @throws GameActionException if the radius is negative (and not -1)
     *
     * @battlecode.doc.costlymethod
     */
    RobotInfo[] senseNearbyRobots(int radiusSquared, Team team) throws GameActionException;

    /**
     * Returns all robots of a given team that can be sensed within a certain
     * radius of a specified location. The objects are returned in no particular
     * order.
     *
     * @param center        center of the given search radius
     * @param radiusSquared return robots this distance away from the center of
     *                      this robot; if -1 is passed, all robots within vision
     *                      radius are returned;
     *                      if radiusSquared is larger than the robot's vision
     *                      radius, the vision
     *                      radius is used
     * @param team          filter game objects by the given team; if null is
     *                      passed,
     *                      objects from all teams are returned
     * @return array of RobotInfo objects of the robots you saw
     * @throws GameActionException if the radius is negative (and not -1) or the
     *                             center given is null
     *
     * @battlecode.doc.costlymethod
     */
    RobotInfo[] senseNearbyRobots(MapLocation center, int radiusSquared, Team team) throws GameActionException;

    /**
     * Given a senseable location, returns whether that location is passable (a
     * wall).
     * 
     * @param loc the given location
     * @return whether that location is passable
     * @throws GameActionException if the robot cannot sense the given location
     *
     * @battlecode.doc.costlymethod
     */
    boolean sensePassability(MapLocation loc) throws GameActionException;

    /**
     * Senses the map info at a location.
     * MapInfo includes walls, dirt, cheese mines, and cheese.
     * 
     * @param loc to sense map at
     * @return MapInfo describing map at location
     * @throws GameActionException if location can not be sensed
     *
     * @battlecode.doc.costlymethod
     */
    MapInfo senseMapInfo(MapLocation loc) throws GameActionException;

    /**
     * Return map info for all senseable locations.
     * MapInfo includes walls, dirt, cheese mines, and cheese.
     *
     * @return MapInfo about all locations within vision radius
     *
     * @battlecode.doc.costlymethod
     */
    MapInfo[] senseNearbyMapInfos();

    /**
     * Return map info for all senseable locations within a radius squared.
     * If radiusSquared is larger than the robot's vision radius, uses the robot's
     * vision radius instead. If -1 is passed, all locations within vision radius
     * are returned.
     * MapInfo includes walls, dirt, cheese mines, and cheese.
     *
     * @param radiusSquared the squared radius of all locations to be returned
     * @return MapInfo about all locations within vision radius
     * @throws GameActionException if the radius is negative (and not -1)
     *
     * @battlecode.doc.costlymethod
     */
    MapInfo[] senseNearbyMapInfos(int radiusSquared) throws GameActionException;

    /**
     * Return map info for all senseable locations within vision radius of a center
     * location.
     * MapInfo includes walls, dirt, cheese mines, and cheese.
     *
     * @param center the center of the search area
     * @return MapInfo about all locations within vision radius
     * @throws GameActionException if center is null
     *
     * @battlecode.doc.costlymethod
     */
    MapInfo[] senseNearbyMapInfos(MapLocation center) throws GameActionException;

    /**
     * Return map info for all senseable locations within a radius squared of a
     * center location.
     * If radiusSquared is larger than the robot's vision radius, uses the robot's
     * vision radius instead. If -1 is passed, all locations within vision radius
     * are returned.
     * MapInfo includes walls, dirt, cheese mines, and cheese.
     *
     * @param center        the center of the search area
     * @param radiusSquared the squared radius of all locations to be returned
     * @return MapInfo about all locations within vision radius
     * @throws GameActionException if the radius is negative (and not -1)
     *
     * @battlecode.doc.costlymethod
     */
    MapInfo[] senseNearbyMapInfos(MapLocation center, int radiusSquared) throws GameActionException;

    /**
     * Returns the location adjacent to current location in the given direction.
     *
     * @param dir the given direction
     * @return the location adjacent to current location in the given direction
     *
     * @battlecode.doc.costlymethod
     */
    MapLocation adjacentLocation(Direction dir);

    /**
     * Returns a list of all locations within the given vision cone of a location.
     * If radiusSquared is larger than the robot's vision radius, uses the robot's
     * vision radius instead.
     *
     * Checks that radiusSquared is non-negative.
     *
     * @param center        the given location
     * @param radiusSquared return locations within this distance away from center
     * @return list of locations on the map and within radiusSquared of center
     * @throws GameActionException if the radius is negative (and not -1)
     *
     * @battlecode.doc.costlymethod
     */
    MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared) throws GameActionException;

    // ***********************************
    // ****** READINESS METHODS **********
    // ***********************************

    /**
     * Tests whether the robot can act.
     * 
     * @return true if the robot can act
     *
     * @battlecode.doc.costlymethod
     */
    boolean isActionReady();

    /**
     * Returns the number of action cooldown turns remaining before this unit can
     * act again.
     * When this number is strictly less than {@link GameConstants#COOLDOWN_LIMIT},
     * isActionReady()
     * is true and the robot can act again. This number decreases by
     * {@link GameConstants#COOLDOWNS_PER_TURN} every turn.
     *
     * @return the number of action turns remaining before this unit can act again
     *
     * @battlecode.doc.costlymethod
     */
    int getActionCooldownTurns();

    /**
     * Tests whether the robot can move.
     * 
     * @return true if the robot can move
     *
     * @battlecode.doc.costlymethod
     */
    boolean isMovementReady();

    /**
     * Returns the number of movement cooldown turns remaining before this unit can
     * move again.
     * When this number is strictly less than {@link GameConstants#COOLDOWN_LIMIT},
     * isMovementReady()
     * is true and the robot can move again. This number decreases by
     * {@link GameConstants#COOLDOWNS_PER_TURN} every turn.
     *
     * @return the number of cooldown turns remaining before this unit can move
     *         again
     *
     * @battlecode.doc.costlymethod
     */
    int getMovementCooldownTurns();

    // ***********************************
    // ****** MOVEMENT METHODS ***********
    // ***********************************

    /**
     * Checks whether this robot can move one step in the direction it is facing.
     * Returns false if the robot is not in a mode that can move, if the target
     * location is not on the map, if the target location is occupied, if the target
     * location is impassible, or if there are cooldown turns remaining.
     *
     * @return true if it is possible to call <code>move</code> without an exception
     *
     * @battlecode.doc.costlymethod
     */
    boolean canMoveForward();

    /**
     * Moves one step in the direction the robot is facing.
     *
     * @throws GameActionException if the robot cannot move one step in this
     *                             direction, such as cooldown being too high, the
     *                             target location being
     *                             off the map, or the target destination being
     *                             occupied by another robot,
     *                             or the target destination being impassible.
     *
     * @battlecode.doc.costlymethod
     */
    void moveForward() throws GameActionException;

    /**
     * Checks whether this robot can turn a certain number of 45 degree steps clockwise.
     * @param steps 
     * @return
     */
    boolean canTurnCW(int steps);

    /**
     * Turns a certain number of 45 degree steps clockwise.
     * @param steps
     * @throws GameActionException
     */
    void turnCW(int steps) throws GameActionException;

    /**
     * Checks whether this robot can turn a certain number of 45 degree steps counter-clockwise.
     * @param steps 
     * @return
     */
    boolean canTurnCCW(int steps);

    /**
     * Turns a certain number of 45 degree steps counter-clockwise.
     * @param steps
     * @throws GameActionException
     */
    void turnCCW(int steps) throws GameActionException;

    // ***********************************
    // *********** BUILDING **************
    // ***********************************

    /**
     * Returns the current cheese cost for an allied rat king to spawn a rat.
     * 
     * @return the amount of cheese that would be needed to spawn another rat
     * 
     * @battlecode.doc.costlymethod
     */
    int getCurrentRatCost();

    /**
     * Checks if a rat king can spawn a robot at the given location.
     * Robots can spawn within a circle of radius of sqrt(4) of the rat king.
     * 
     * @param loc  the location to spawn the robot at
     * @return true if robot can be built at loc
     * 
     * @battlecode.doc.costlymethod
     */
    boolean canBuildRobot(MapLocation loc);

    /**
     * Spawns a robot at the given location.
     * Robots can spawn within a circle of radius of sqrt(4) of the rat king.
     * 
     * @param loc  the location to spawn the robot at
     * 
     * @battlecode.doc.costlymethod
     */
    void buildRobot(MapLocation loc) throws GameActionException;

    /**
     * Checks if a rat can become a rat king, when 7 allied rats are in the 3x3 square
     * centered at this rat's location and the ally team has 50 cheese. 
     * All tiles in the 3x3 square must be passible.
     * 
     * @return true if this rat can become a rat king
     * 
     * @battlecode.doc.costlymethod
     */
    boolean canBecomeRatKing();

    /**
     * Upgrades this rat into a rat king if possible, when 7 allied rats are in the 3x3 square
     * centered at this rat's location and the ally team has 50 cheese.
     * 
     * Other rats in the 3x3 square will be killed.
     * 
     * @battlecode.doc.costlymethod
     */
    void becomeRatKing() throws GameActionException;

    /**
     * Tests whether this robot can place dirt at the given location.
     * 
     * @param loc
     * @throws GameActionException
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean canPlaceDirt(MapLocation loc);

    /**
     * Places dirt at the given location.
     * 
     * @param loc the location to place the dirt
     * 
     * @battlecode.doc.costlymethod
     */
    void placeDirt(MapLocation loc) throws GameActionException;

     /**
     * Tests whether this robot can place dirt at the given location.
     * 
     * @param loc
     * @throws GameActionException
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean canRemoveDirt(MapLocation loc);

    /**
     * Removes dirt from the given location.
     * 
     * @param loc the location to remove dirt from
     * 
     * @battlecode.doc.costlymethod
     */
    void removeDirt(MapLocation loc) throws GameActionException;

    /**
     * Tests whether this robot can place a rat trap at the given location.
     * @param loc
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean canPlaceRatTrap(MapLocation loc);

    /**
     * Places a rat trap at the given location.
     * @param loc
     * 
     * @battlecode.doc.costlymethod
     */
    public void placeRatTrap(MapLocation loc) throws GameActionException;

    /**
     * Tests whether this robot can remove a rat trap at the given location.
     * @param loc
     * @throws GameActionException
     * 
     * @battlecode.doc.costlymethod
     */
    public boolean canRemoveRatTrap(MapLocation loc);

    /**
     * Removes the rat trap at the given location.
     * @param loc
     * @throws GameActionException
     * 
     * @battlecode.doc.costlymethod
     */
    public void removeRatTrap(MapLocation loc) throws GameActionException;

    /**
     * Tests whether this robot can place a cat trap at the given location.
     * @param loc
     */
    public boolean canPlaceCatTrap(MapLocation loc);

    /**
     * Places a cat trap at the given location.
     * @param loc
     */
    public void placeCatTrap(MapLocation loc) throws GameActionException;

    /**
     * Tests whether this robot can remove a cat trap at the given location.
     * @param loc
     * @throws GameActionException
     */
    public boolean canRemoveCatTrap(MapLocation loc);

    /**
     * Removes the cat trap at the given location.
     * @param loc
     * @throws GameActionException
     */
    public void removeCatTrap(MapLocation loc) throws GameActionException;

    // ****************************
    // ***** ATTACK / HEAL ********
    // ****************************

    /**
     * Tests whether this robot can attack the given location. Types of
     * attacks for specific units determine whether or not towers, other
     * robots, or empty tiles can be attacked.
     *
     * @param loc target location to attack
     * @return whether it is possible to attack the given location
     *
     * @battlecode.doc.costlymethod
     */
    boolean canAttack(MapLocation loc);

    // TODO: updaate docstrings from paint related stuff to rat related stuff
    /**
     * Performs the specific attack for this robot type, defaulting to the
     * primary color
     *
     * @param loc the target location to attack (for splashers, the center location)
     *            Note: for a tower, leaving loc null represents an area attack
     * @throws GameActionException if conditions for attacking are not satisfied
     *
     * @battlecode.doc.costlymethod
     */
    void attack(MapLocation loc) throws GameActionException;

    // ***********************************
    // ****** COMMUNICATION METHODS ******
    // ***********************************

    /**
     * Sends a message (contained in an int, so 4 bytes) to all locations within squeaking range.
     * 
     * @param messageContent an int representing the content of the
     * message (up to 4 bytes)
     * 
     * @battlecode.doc.costlymethod
     */
    void squeak(int messageContent);

    /**
     * Reads all squeaks sent to this unit within the past 5 rounds if roundNum = -1, or only
     * squeaks sent from the specified round otherwise
     * 
     * @param roundNum the round number to read messages from, or -1 to read all
     *                 messages in the queue
     * @return All messages of the specified round, or all messages from the past 5
     *         round.
     * 
     * @battlecode.doc.costlymethod
     */
    Message[] readSqueaks(int roundNum);

    /**
     * Writes a value to the shared array at the given index.
     * This is only allowed for rat kings.
     * 
     * @param index the index to write to, between 0 and 63
     * @param value the value to write in the index (must be between 0 and 1023)
     * @throws GameActionException if the action is invalid
     * 
     * @battlecode.doc.costlymethod
     */
    void writeSharedArray(int index, int value) throws GameActionException;

    /**
     * Reads a value from the shared array at the given index.
     * All rats and rat kings can read from the shared array.
     * 
     * @param index the index to read from, between 0 and 63
     * @return the value stored at the given index (between 0 and 1023)
     * @throws GameActionException if the action is invalid
     * 
     * @battlecode.doc.costlymethod
     */
    int readSharedArray(int index) throws GameActionException;

    /**
     * Writes a value to the persistent array at the given index.
     * This is only allowed for rat kings.
     * 
     * @param index the index to write to, between 0 and 4
     * @param value the value to write in the index (must be between 0 and 1023)
     * @throws GameActionException if the action is invalid
     * 
     * @battlecode.doc.costlymethod
     */
    void writePersistentArray(int index, int value) throws GameActionException;

    /**
     * Reads a value from the persistent array at the given index.
     * All rats and rat kings can read from the persistent array.
     * 
     * @param index the index to read from, between 0 and 4
     * @return the value stored at the given index (between 0 and 1023)
     * @throws GameActionException if the action is invalid
     * 
     * @battlecode.doc.costlymethod
     */
    int readPersistentArray(int index) throws GameActionException;

    // ***********************************
    // ****** OTHER ACTION METHODS *******
    // ***********************************

    /**
     * Tests whether you can transfer cheese to a given rat king.
     * 
     * You can give cheese to an allied rat king if you are a rat, can act
     * at the given location, and have enough raw cheese in your local stash.
     * 
     * @param loc    the location of the rat king to transfer cheese to
     * @param amount the amount of cheese to transfer. Positive to give cheese.
     * @return true if the robot can transfer cheese to a rat king at the given
     *         location
     */
    boolean canTransferCheese(MapLocation loc, int amount);

    /**
     * Throws robot in the robot direction
     * 
     * @param dir the location 
     * @battlecode.doc.costlymethod
     */
    void throwRat(Direction dir) throws GameActionException;

    /**
     * Tests whether the robot can throw a carried robot in the specified direction.
     * 
     * @param dir the direction to throw the robot
     * @throws GameActionException if the robot is not able to transfer cheese to the
     *                             location
     */
    boolean canThrowRat(Direction dir) throws GameActionException;

    /**
     * Tests whether the robot can grab (carry) a robot at the specified location.
     *
     * @param loc the location to grab from (must be adjacent)
     * @return true if this robot can pick up a robot at loc
     */
    boolean canCarryRat(MapLocation loc);

    /**
     * Causes this robot to pick up (grab) a robot at the specified location.
     *
     * @param loc the location to pick up from (must be adjacent)
     * @throws GameActionException if this robot cannot pick up the target
     */
    void carryRat(MapLocation loc) throws GameActionException;

    /**
     * Destroys the robot.
     *
     * @battlecode.doc.costlymethod
     **/
    void disintegrate();

    /**
     * Causes your team to lose the game. It's like typing "gg."
     *
     * @battlecode.doc.costlymethod
     */
    void resign();

    // ***********************************
    // ******** DEBUG METHODS ************
    // ***********************************

    /**
     * Sets the indicator string for this robot for debugging purposes. Only the
     * first
     * {@link GameConstants#INDICATOR_STRING_MAX_LENGTH} characters are used.
     *
     * @param string the indicator string this round
     *
     * @battlecode.doc.costlymethod
     */
    void setIndicatorString(String string);

    /**
     * Draw a dot on the game map for debugging purposes.
     *
     * @param loc   the location to draw the dot
     * @param red   the red component of the dot's color
     * @param green the green component of the dot's color
     * @param blue  the blue component of the dot's color
     * @throws GameActionException if the location is off the map
     *
     * @battlecode.doc.costlymethod
     */
    void setIndicatorDot(MapLocation loc, int red, int green, int blue) throws GameActionException;

    /**
     * Draw a line on the game map for debugging purposes.
     *
     * @param startLoc the location to draw the line from
     * @param endLoc   the location to draw the line to
     * @param red      the red component of the line's color
     * @param green    the green component of the line's color
     * @param blue     the blue component of the line's color
     * @throws GameActionException if any location is off the map
     *
     * @battlecode.doc.costlymethod
     */
    void setIndicatorLine(MapLocation startLoc, MapLocation endLoc, int red, int green, int blue)
            throws GameActionException;

    /**
     * Adds a marker to the timeline at the current
     * round for debugging purposes.
     * Only the first
     * {@link GameConstants#TIMELINE_LABEL_MAX_LENGTH} characters are used.
     * 
     * @param label the label for the timeline marker
     * @param red   the red component of the marker's color
     * @param green the green component of the marker's color
     * @param blue  the blue component of the marker's color
     * 
     * @battlecode.doc.costlymethod
     */
    void setTimelineMarker(String label, int red, int green, int blue);
}
// TODO: update bytecode costs, particularly for new methods + methods that got renamed from last year