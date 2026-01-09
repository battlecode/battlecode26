package colemanplayerv0v1;

import java.util.Arrays;
import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * RobotPlayer is the class that describes your main robot strategy. The run()
 * method inside this class is like your main function: this is what we'll call
 * once your robot is created!
 */
public class RobotPlayer {

    /**
     * We will use this variable to count the number of turns this robot has
     * been alive. You can use static variables like this to save any
     * information you want. Keep in mind that even though these variables are
     * static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;
    static RobotController rc;

    static final int HOLDOVER_TURNS = 2;
    static final int CHEESE_CONSUMPTION = GameConstants.RATKING_CHEESE_CONSUMPTION; // Saves a bytecode by making it a local static field (I think)
    static final int CHEESE_RETURN_QTY = 10;

    static MapLocation kingLocation = null;

    public static enum Jobs {
        KING,
        CHEESE_COURIER,
        SCOUT,
        WARRIOR,
        SACRIFICE,
        BUILDER
    }

    public static Jobs job;
    public static MapLocation cheeseMines[] = new MapLocation[10];
    public static int cheeseMineCount = 0;
    public static MapLocation occupiedCheeseMines[] = new MapLocation[10];
    public static int occupiedCheeseMineCount = 0;
    static MapLocation cheeseToReturnTo = null;

    static Direction spiritualExploreDirection = null; // For crazy ivans

    /**
     * A random number generator. We will use this RNG to make some random
     * moves. The Random class is provided by the java.util.Random import at the
     * top of this file. Here, we *seed* the RNG with a constant number (6147);
     * this makes sure we get the same sequence of numbers every time this code
     * is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /**
     * Array containing all the possible movement directions.
     */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,};

    /**
     * run() is the method that is called when a robot is instantiated in the
     * Battlecode world. It is like the main function for your robot. If this
     * method returns, the robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from
     * this robot, and to get information on its current status. Essentially
     * your portal to interacting with the world.
     *
     */
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        // System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            if (turnCount == 0) {
                // This is our first turn; we can perform any initial setup here.
                if (rc.getType() == battlecode.common.UnitType.BABY_RAT) {
                    initBaby(rc);
                }
            }

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                if (rc.getType() == battlecode.common.UnitType.RAT_KING) {
                    runKing(rc);
                } else if (rc.getType() == battlecode.common.UnitType.BABY_RAT) {
                    runBaby(rc);
                }
            } catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    public static void runKing(RobotController rc) throws GameActionException {
        int cheese = rc.getGlobalCheese();
        if ((cheese >= CHEESE_CONSUMPTION * HOLDOVER_TURNS + rc.getCurrentRatCost() && rc.getRoundNum() % 999 == 0 && rc.getRoundNum() < 200 && rc.getRoundNum() > 180) || (cheese > rc.getCurrentRatCost() && rc.getRoundNum() % 50 == 10)) {
            System.out
                    .println("Turn " + turnCount + "SPAWNING RAT at location " + rc.getLocation() + " with cheese " + cheese);
            Direction dir = rc.getLocation().directionTo(new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2));
            MapLocation loc = rc.getLocation().add(dir).add(dir);
            rc.buildRobot(loc);
            commToNewRat(rc, loc);
        } else {
            // TODO System.out.println("Turn " + turnCount + " NOT ENOUGH CHEESE to spawn RAT at location " + rc.getLocation() + " with cheese "+ cheese);
        }
    }

    public static void initBaby(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo bot : nearbyBots) {
            if (bot.getType() == battlecode.common.UnitType.RAT_KING && bot.getTeam() == rc.getTeam()) {
                kingLocation = bot.getLocation();
                break;
            }
        }
        if (kingLocation == null) { // TODO better handling (using squeaking)
            rc.turn(rc.getDirection().opposite());
            nearbyBots = rc.senseNearbyRobots();
            for (RobotInfo bot : nearbyBots) {
                if (bot.getType() == battlecode.common.UnitType.RAT_KING && bot.getTeam() == rc.getTeam()) {
                    kingLocation = bot.getLocation();
                    break;
                }
            }
        }
        if (rc.getRoundNum() < 10 || rc.getRoundNum() % 5 == 0 || true) { // TODO
            job = Jobs.CHEESE_COURIER;
        }
        spiritualExploreDirection = rc.getDirection();
    }

    public static void explore(RobotController rc) throws GameActionException {
        if (rc.getDirection() == spiritualExploreDirection) {
            // Placeholder code, wander randomly
            if (rc.canMoveForward()) {
                System.out.println("Turn " + turnCount + "MOVING" + rc.getDirection());
                rc.moveForward();
                rc.turn(Direction.values()[rng.nextInt(8)]);
            } else if (rc.canTurn()) {
                System.out.println("couldn't move forward on turn " + turnCount + " at location " + rc.getLocation() + rc.getDirection());
                // If we can't move forward, try to turn a random direction.
                int randomDirection = rng.nextInt(8);
                rc.turn(Direction.values()[randomDirection]);
                spiritualExploreDirection = Direction.values()[randomDirection];
            }
        } else {
            rc.turn(spiritualExploreDirection);
            if (rc.canMoveForward()) {
                System.out.println("Turn " + turnCount + "MOVING" + rc.getDirection());
                rc.moveForward();
            } // If we can't move, we've already used our turn, so too bad, we have to wait
        }
    }

    static void grabVisibleCheese(RobotController rc, MapInfo[] locs) throws GameActionException {
        for (MapInfo loc : locs) {
            if (loc.getCheeseAmount() > 0) {
                if (rc.canPickUpCheese(loc.getMapLocation())) {
                    rc.pickUpCheese(loc.getMapLocation());
                    System.out.println("Picked up cheese at " + loc.getMapLocation() + " now have " + rc.getRawCheese());
                    break;
                } else if (rc.isTurningReady() && rc.getLocation().distanceSquaredTo(loc.getMapLocation()) <= 2) { // This branch will only happen if, at some point in the future, we add stuff we haven't sensed this turn (e.g. comms) to locs.
                    Direction toCheese = rc.getLocation().directionTo(loc.getMapLocation());
                    rc.turn(toCheese);
                    rc.pickUpCheese(loc.getMapLocation());
                    break;
                } else {
                    walkTowards(loc.getMapLocation());
                    break;
                }
            }
        }
    }

    static void seekCheeseMines(RobotController rc) throws GameActionException {
        if (cheeseToReturnTo != null) {
            walkTowards(cheeseToReturnTo);
            if (rc.getLocation().distanceSquaredTo(cheeseToReturnTo) <= 8) {
                cheeseToReturnTo = null;
            }
            return;
        }
        grabVisibleCheese(rc, rc.senseNearbyMapInfos());
        explore(rc);
        // at king, go to cheese
        /*
        if (rc.senseMapInfo(rc.getLocation()).hasCheeseMine()) {
            // Spin in a circle looking for cheese
            if (rc.canTurn()) {
                rc.turn(rc.getDirection().rotateRight().rotateRight());
            }
            MapInfo[] locs = rc.senseNearbyMapInfos();
            grabVisibleCheese(rc, locs);
        }
        else if (cheeseMineCount > occupiedCheeseMineCount) {
            int attempt = 0;
            MapLocation tgt = cheeseMines[rc.getID() % cheeseMineCount];
            boolean flag = true;

            while (!flag && attempt < cheeseMineCount) {
                flag = true;
                for (MapLocation occ : occupiedCheeseMines) {
                    if (occ != null && occ.equals(tgt)) {
                        flag = false;
                        break;
                    }
                }
                attempt += 1;
                tgt = cheeseMines[(rc.getID() + attempt) % cheeseMineCount];
            }
            if (attempt < cheeseMineCount) { // We have a cheese mine that we think is ours
            walkTowards(cheeseMines[rc.getID() % cheeseMineCount]);
                if (rc.canTurn()) {
                    rc.turn(rc.getLocation().directionTo(cheeseMines[rc.getID() % cheeseMineCount]));
                }
            }
        } else {
            // no known cheese mines, wander randomly
            explore(rc);
        }
        */
    }

    public static void runBaby(RobotController rc) throws GameActionException {
        MapInfo[] locs = rc.senseNearbyMapInfos();
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (MapInfo loc : locs) {
            if (loc.hasCheeseMine()) {
                if (Arrays.stream(cheeseMines).noneMatch(x -> x != null && x.equals(loc.getMapLocation()))) { // I HATE JAVA ARRAYS
                    cheeseMines[cheeseMineCount] = loc.getMapLocation();
                    cheeseMineCount += 1;
                }
                for (RobotInfo bot : nearbyBots) {
                    if (bot.getType() == UnitType.BABY_RAT && bot.getTeam() == rc.getTeam() && bot.getLocation().distanceSquaredTo(loc.getMapLocation()) <= 8) { // An ally has been spotted next to this cheese mine; we assume that he's got it covered
                        // TODO replace sensing with something done via soft squeaking
                        if (Arrays.stream(occupiedCheeseMines).noneMatch(x -> x != null && x.equals(loc.getMapLocation()))) {
                            occupiedCheeseMines[occupiedCheeseMineCount] = loc.getMapLocation();
                            occupiedCheeseMineCount += 1;
                        }
                        break;
                    }
                }
            }
        }
        if (job == Jobs.CHEESE_COURIER) {
            if (rc.getRawCheese() < CHEESE_RETURN_QTY) {
                grabVisibleCheese(rc, locs);
                seekCheeseMines(rc);
            } else if (kingLocation != null) {
                if (cheeseToReturnTo == null) {
                    for (MapInfo loc : locs) {
                        if (loc.getCheeseAmount() > 0) {
                            cheeseToReturnTo = loc.getMapLocation();
                            break;
                        }
                    }
                }
                // at cheese, go to king
                walkTowards(kingLocation);
                if (rc.getLocation().distanceSquaredTo(kingLocation) <= 8) {
                    if (rc.canTransferCheese(rc.getLocation().add(rc.getLocation().directionTo(kingLocation)), rc.getRawCheese())) {
                        rc.transferCheese(rc.getLocation().add(rc.getLocation().directionTo(kingLocation)), rc.getRawCheese());
                    }
                }
            } else {
                // At cheese, but we don't know wherre king is :(
                explore(rc);
            }
        } else {
            // Placeholder code, wander randomly
            explore(rc);
        }
    }

    private static void commToNewRat(RobotController rc, MapLocation loc) {
        // TODO Auto-generated method stub

    }

    private static boolean passable(RobotController rc, Direction d) throws GameActionException {
        MapLocation adjacentLocation = rc.getLocation().add(d);
        return rc.sensePassability(adjacentLocation) && !rc.canSenseRobotAtLocation(adjacentLocation);
    }

    public static boolean safe(RobotController rc, Direction d) throws GameActionException {
        if (!passable(rc, d)) {
            return false;
        }
        MapLocation adjacentLocation = rc.getLocation().add(d);
        if (rc.getType() == UnitType.BABY_RAT) {
            RobotInfo[] enemies = rc.senseNearbyRobots();
            for (RobotInfo enemy : enemies) {
                if (enemy.team != rc.getTeam()) {
                    if (adjacentLocation.distanceSquaredTo(enemy.getLocation()) <= 8) {
                        return false;
                    }
                }
            }
        }

        return true;

    }

    private static Direction bugDirection = null;

    public static Direction walkTowards(MapLocation target) throws GameActionException {
        bugDirection = null;
        if (!rc.isMovementReady()) {
            return null;
        }

        if (rc.getLocation().equals(target)) {
            return null;
        }

        Direction d = rc.getLocation().directionTo(target);
        rc.turn(d);
        Direction result = null;
        if (rc.canMove(d)) {
            rc.move(d);
            bugDirection = null;
        } else {
            if (bugDirection == null) {
                bugDirection = d;
            }
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(d)) {
                    rc.move(bugDirection);
                    result = bugDirection;
                    bugDirection = bugDirection.rotateLeft();
                    break;
                } else {
                    bugDirection = bugDirection.rotateRight();
                }
            }
        }
        return result;
    }

    public static Direction walkAway(MapLocation target) throws GameActionException {
        bugDirection = null;
        if (!rc.isMovementReady()) {
            return null;
        }

        if (rc.getLocation().equals(target)) {
            return null;
        }

        Direction d = target.directionTo(rc.getLocation());
        Direction result = null;
        if (passable(rc, d)) {
            rc.move(d);
            bugDirection = null;
        } else {
            if (bugDirection == null) {
                bugDirection = d;
            }
            for (int i = 0; i < 8; i++) {
                if (passable(rc, bugDirection)) {
                    rc.move(bugDirection);
                    result = bugDirection;
                    bugDirection = bugDirection.rotateLeft();
                    break;
                } else {
                    bugDirection = bugDirection.rotateRight();
                }
            }
        }
        return result;
    }
}
