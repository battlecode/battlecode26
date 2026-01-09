package ruthplayer;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import java.util.EnumMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Stream;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what
 * we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been
     * alive.
     * You can use static variables like this to save any information you want. Keep
     * in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between
     * your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided
     * by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant
     * number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very
     * useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    // bugnav
    static MapLocation prevDest = null;
    static HashSet<MapLocation> line = null;
    static int obstacleStartDist = 0;

    static boolean isTracing = false;
    static int smallestDistance = 10000000;
    static MapLocation closestLocation = null;
    static Direction tracingDir = null;

    // static RobotState state = RobotState.STARTING;
    static MapLocation originalRatKing = null;
    static MapLocation goalLoc = null;

    static MapLocation cheeseMine = null;

    /**
     * run() is the method that is called when a robot is instantiated in the
     * Battlecode world.
     * It is like the main function for your robot. If this method returns, the
     * robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this
     *           robot, and to get
     *           information on its current status. Essentially your portal to
     *           interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you
        // run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in
            // an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At
            // the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to
            // do.

            // Try/catch blocks stop unhandled exceptions, which cause your robot to
            // explode.
            try {

                if (rc.getType().isRatKingType()) {
                    runRatKing(rc);
                } else {
                    runBabyRat(rc);
                }
                turnCount += 1; // We have now been alive for one more turn!

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop
                // again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for
            // another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction
        // imminent...
    }

    public static void runRatKing(RobotController rc) throws GameActionException {
        // write my current location to front of global array
        rc.writeSharedArray(0, rc.getLocation().x);
        rc.writeSharedArray(1, rc.getLocation().y);

        if ((rc.getGlobalCheese() - rc.getCurrentRatCost()) > 2000) {
            int randomDirection = rng.nextInt(8);
            MapLocation randomLocation = rc.getLocation().add(directions[randomDirection])
                    .add(directions[randomDirection]);

            if (rc.canBuildRat(randomLocation)) {
                rc.buildRat(randomLocation);
            }
        }

        Message[] squeaks = rc.readSqueaks(turnCount);

        if (squeaks.length > 0) {

        }
    }

    public static void runBabyRat(RobotController rc) throws GameActionException {
        // if holding cheese, deliver back to ratking
        if (turnCount == 0) {
            originalRatKing = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
        }

        if (rc.getRawCheese() > 0) {
            bug2(rc, originalRatKing);
        } else {
            // if current target doesn't exist, set random target at least 6 away and go to
            // it
            if (goalLoc == null || rc.getLocation().distanceSquaredTo(goalLoc) < 36) {
                // if (cheeseMine == null) {
                // goalLoc = new MapLocation(rng.nextInt(rc.getMapWidth()),
                // rng.nextInt(rc.getMapHeight()));
                // } else {
                // goalLoc = cheeseMine;
                // }

                goalLoc = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));

            }

            bug2(rc, goalLoc);
        }
        // detect nearby robots. If ratking, set original ratking to it
        for (RobotInfo r : rc.senseNearbyRobots()) {
            // if ratking and not holding cheese, set to original ratking; otherwise give it
            // cheese
            if (r.getType().isRatKingType() && r.getTeam().equals(rc.getTeam())) {

                // System.out.println("I have " + rc.getRawCheese() + " cheese");
                // System.out.println(rc.getLocation() + " " + r.getLocation());

                if (rc.getRawCheese() > 0 && rc.canTransferCheese(r.getLocation(), rc.getRawCheese())) {
                    System.out.println("TRANSFERRING CHEESE HERE HELLO");
                    System.out.println(rc.getGlobalCheese());
                    rc.transferCheese(r.getLocation(), rc.getRawCheese());
                    System.out.println(rc.getGlobalCheese());
                }
                // originalRatKing = r.getLocation();
                goalLoc = null;

                if (cheeseMine != null) {
                    rc.squeak(10000 + r.getLocation().x * 100 + r.getLocation().y);
                }
            } else if (r.getType().isCatType()) {
                if (rc.getAllCheese() > 1000 && rc.canPlaceCatTrap(rc.getLocation().add(rc.getDirection()))) {
                    System.out.println("Time to trap the cat");
                    rc.placeCatTrap(rc.getLocation().add(rc.getDirection()));
                }
            }

            // if enemy rat, try to grab / bite
            if (r.getTeam().equals(rc.getTeam().opponent())) {
                if (rc.canCarryRat(r.getLocation())) {
                    System.out.println("CARRYING HERE " + rc.getLocation() + " " + r.getLocation());
                    rc.carryRat(r.getLocation());
                } else if (rc.canAttack(r.getLocation())) {
                    System.out.println("ATTACKING HERE " + rc.getLocation() + " " + r.getLocation());
                    rc.attack(r.getLocation(), rc.getRawCheese());
                }
            }

        }
        if (rc.getAllCheese() > 1000 && rc.canPlaceRatTrap(rc.getLocation().add(rc.getDirection()))) {
            System.out.println("Time to trap rats");
            rc.placeRatTrap(rc.getLocation().add(rc.getDirection()));
        }

        // if sense cheese, pickup
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.getCheeseAmount() > 0) {
                if (rc.canPickUpCheese(info.getMapLocation())) {
                    rc.pickUpCheese(info.getMapLocation());
                }
            }

            if (info.hasCheeseMine()) {
                cheeseMine = info.getMapLocation();
                goalLoc = cheeseMine;
            }
        }

    }

    public static void bug2(RobotController rc, MapLocation target) throws GameActionException {

        if (!target.equals(prevDest)) {

            prevDest = target;
            line = createLine(rc.getLocation(), target);

            obstacleStartDist = 0;

            isTracing = false;
            // smallestDistance = 10000000;
            // closestLocation = null;
            // tracingDir = null;
        }

        for (MapLocation loc : line) {
            rc.setIndicatorDot(loc, 255, 0, 0);
        }

        if (!isTracing) {
            Direction dir = rc.getLocation().directionTo(target);
            rc.setIndicatorDot(rc.getLocation().add(dir), 255, 0, 0);

            if (rc.canRemoveDirt(rc.getLocation().add(dir))) {
                System.out.println("digggg");
                rc.removeDirt(rc.getLocation().add(dir));
            }

            if (rc.canMove(dir)) {
                rc.move(dir);
            } else {
                isTracing = true;
                obstacleStartDist = rc.getLocation().distanceSquaredTo(target);
                tracingDir = dir;
            }
        } else {
            if (line.contains(rc.getLocation()) && rc.getLocation().distanceSquaredTo(target) < obstacleStartDist) {
                isTracing = false;
            }

            for (int i = 0; i < 9; i++) {

                if (rc.canRemoveDirt(rc.getLocation().add(tracingDir))) {
                    System.out.println("digggg");
                    rc.removeDirt(rc.getLocation().add(tracingDir));
                }

                if (rc.canMove(tracingDir)) {
                    rc.move(tracingDir);
                    tracingDir = tracingDir.rotateRight();
                    tracingDir = tracingDir.rotateRight();
                    break;
                } else {
                    tracingDir = tracingDir.rotateLeft();
                }
            }
        }
    }

    // Bresenham's line algorithm for bug2
    public static HashSet<MapLocation> createLine(MapLocation a, MapLocation b) {
        HashSet<MapLocation> locs = new HashSet<>();
        int x = a.x, y = a.y;
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        int sx = (int) Math.signum(dx);
        int sy = (int) Math.signum(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        int d = Math.max(dx, dy);
        int r = d / 2;
        if (dx > dy) {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                x += sx;
                r += dy;
                if (r >= dx) {
                    locs.add(new MapLocation(x, y));
                    y += sy;
                    r -= dx;
                }
            }
        } else {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                y += sy;
                r += dx;
                if (r >= dy) {
                    locs.add(new MapLocation(x, y));
                    x += sx;
                    r -= dy;
                }
            }
        }
        locs.add(new MapLocation(x, y));
        return locs;
    }
}
