package maxplayer;

import battlecode.common.*;

import java.util.Random;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    static int turnsSinceCarry = 100;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
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

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!
            turnsSinceCarry += 1;

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                
                // Every 10 turns, print out what type of robot we are.
                if (turnCount % 100 == 0) {
                    System.out.println("Turn " + turnCount + ": I am a " + rc.getType().toString());
                }
                
                // BABY_RAT: throw as soon as possible; otherwise turn toward any sensed enemy cat to set up a throw.
                if (rc.getType() == UnitType.BABY_RAT) {
                    // Immediate throw if possible
                    if (rc.canThrowRat()) {
                        rc.throwRat();
                        rc.throwRat();
                        rc.throwRat();
                        continue;
                    }

                    // If carrying, aim toward the first sensed enemy cat and prioritize turning
                    RobotInfo[] nearby = rc.senseNearbyRobots();
                    RobotInfo targetCat = null;
                    for (RobotInfo info : nearby) {
                        if (info.team != rc.getTeam() && info.type.isCatType()) {
                            targetCat = info;
                            break; // take the first sensed cat
                        }
                    }

                    if (targetCat != null && rc.getCarrying() != null) {
                        Direction toCat = rc.getLocation().directionTo(targetCat.location);
                        if (toCat != null && toCat != Direction.CENTER) {
                            if (rc.getDirection() != toCat && rc.canTurn(toCat)) {
                                rc.turn(toCat);
                                continue; // prioritize turning to set up next-turn throw
                            }
                        }
                    }
                }
                                
                // Try to move forward one step.
                if (rc.canMoveForward()) {
                    System.out.println("Turn " + turnCount + ": Trying to move " + rc.getDirection());
                    rc.moveForward();
                } else {
                    System.out.println("couldn't move forward on turn " + turnCount + " at location " + rc.getLocation() + " facing " + rc.getDirection());
                    // If we can't move forward, try to turn a random direction.
                    int randomDirection = rng.nextInt(8);
                    
                    if (rc.canTurn()) {
                        rc.turn(directions[randomDirection]);
                    }

                    if (rc.getType() == UnitType.RAT_KING) {
                        MapLocation[] partLocs = rc.getAllPartLocations();
                        
                        for (MapLocation loc : partLocs) {
                            MapLocation newLoc = loc.add(rc.getDirection());

                            if (rc.canBuildRat(newLoc)) {
                                rc.buildRat(newLoc);
                            }
                        }
                    } else if (rc.getType() == UnitType.BABY_RAT) {
                        // Strategy: pick up a nearby rat, carry it to a tile one away from a wall,
                        // face the wall, and repeatedly throw the carried rat against the wall.
                        try {
                            RobotInfo carrying = rc.getCarrying();

                            // If we are carrying a rat, try to find a direction where the next tile
                            // (one step forward) is a wall/impassable. If found, face that wall and throw.
                            if (carrying != null) {
                                MapLocation loc = rc.getLocation();

                                // Find the nearest wall in any cardinal/diagonal direction by scanning outward.
                                Direction nearestWallDir = null;
                                int nearestWallDist = Integer.MAX_VALUE;
                                int maxScan = 6; // how far to look for a wall
                                MapLocation myLoc = rc.getLocation();
                                for (Direction d : directions) {
                                    MapLocation scanLoc = myLoc;
                                    for (int step = 1; step <= maxScan; step++) {
                                        scanLoc = scanLoc.add(d);
                                        if (!rc.onTheMap(scanLoc)) break;
                                        boolean passable = true;
                                        try {
                                            passable = rc.sensePassability(scanLoc);
                                        } catch (GameActionException e) {
                                            passable = false;
                                        }
                                        if (!passable) {
                                            if (step < nearestWallDist) {
                                                nearestWallDist = step;
                                                nearestWallDir = d;
                                            }
                                            break;
                                        }
                                    }
                                }

                                if (nearestWallDir != null) {
                                    // If we're already one tile away from the wall (step == 1), face it and throw.
                                    if (nearestWallDist == 1) {
                                        if (rc.getDirection() != nearestWallDir && rc.canTurn(nearestWallDir)) {
                                            rc.turn(nearestWallDir);
                                        }
                                        if (rc.canThrowRat()) {
                                            rc.throwRat();
                                        }
                                    } else {
                                        // Move toward the tile that is one-away-from-wall in that direction.
                                        MapLocation target = myLoc;
                                        for (int i = 0; i < nearestWallDist - 1; i++) target = target.add(nearestWallDir);
                                        Direction toTarget = rc.getLocation().directionTo(target);
                                        if (toTarget != null && toTarget != Direction.CENTER) {
                                            if (rc.getDirection() != toTarget && rc.canTurn(toTarget)) {
                                                rc.turn(toTarget);
                                            } else if (rc.canMove(toTarget)) {
                                                rc.move(toTarget);
                                            } else if (rc.canMoveForward()) {
                                                rc.moveForward();
                                            }
                                        }
                                    }
                                } else {
                                    // No wall found in scan range: fallback to previous simple wandering
                                    if (rc.canMoveForward()) {
                                        rc.moveForward();
                                    } else if (rc.canTurn()) {
                                        rc.turn(directions[rng.nextInt(directions.length)]);
                                    }
                                }
                            } else {
                                // Not carrying: try to pick up a nearby rat (ally or any carryable rat).
                                boolean picked = false;
                                for (Direction dir : directions) {
                                    MapLocation targetLoc = rc.getLocation().add(dir);
                                    if (rc.canCarryRat(targetLoc)) {
                                        rc.carryRat(targetLoc);
                                        turnsSinceCarry = 0;
                                        picked = true;
                                        break;
                                    }
                                }

                                if (!picked) {
                                    // Prefer to stay near walls: if already adjacent to a wall, face it
                                    // and move along it to find more rats.
                                    MapLocation myLoc = rc.getLocation();
                                    Direction adjacentWall = null;
                                    for (Direction d : directions) {
                                        MapLocation ahead = myLoc.add(d);
                                        if (rc.onTheMap(ahead)) {
                                            boolean passable = true;
                                            try {
                                                passable = rc.sensePassability(ahead);
                                            } catch (GameActionException e) {
                                                passable = false;
                                            }
                                            if (!passable) {
                                                adjacentWall = d;
                                                break;
                                            }
                                        }
                                    }

                                    if (adjacentWall != null) {
                                        // Face the wall
                                        if (rc.getDirection() != adjacentWall && rc.canTurn(adjacentWall)) {
                                            rc.turn(adjacentWall);
                                        } else {
                                            // Try to move along the wall (left or right) to discover rats
                                            Direction[] along = {adjacentWall.rotateLeft(), adjacentWall.rotateRight()};
                                            boolean moved = false;
                                            for (Direction ad : along) {
                                                MapLocation step = myLoc.add(ad);
                                                if (rc.onTheMap(step)) {
                                                    MapLocation checkAhead = step.add(adjacentWall);
                                                    boolean wallThere = false;
                                                    try {
                                                        wallThere = !rc.sensePassability(checkAhead);
                                                    } catch (GameActionException e) {
                                                        wallThere = false;
                                                    }
                                                    if (wallThere && rc.canMove(ad)) {
                                                        rc.move(ad);
                                                        moved = true;
                                                        break;
                                                    }
                                                }
                                            }
                                            if (!moved) {
                                                // Occasionally turn in place to re-orient
                                                if (turnsSinceCarry > 50 && rc.canTurn()) {
                                                    rc.turn(directions[rng.nextInt(directions.length)]);
                                                }
                                            }
                                        }
                                    } else {
                                        // Move toward nearest wall-adjacent tile within a small radius
                                        MapLocation best = null;
                                        int bestDist = Integer.MAX_VALUE;
                                        for (int dx = -3; dx <= 3; dx++) {
                                            for (int dy = -3; dy <= 3; dy++) {
                                                if (dx == 0 && dy == 0) continue;
                                                int cx = myLoc.x + dx;
                                                int cy = myLoc.y + dy;
                                                MapLocation cand = new MapLocation(cx, cy);
                                                if (!rc.onTheMap(cand)) continue;
                                                boolean adjWall = false;
                                                for (Direction d : directions) {
                                                    MapLocation wc = cand.add(d);
                                                    if (!rc.onTheMap(wc)) continue;
                                                    try {
                                                        if (!rc.sensePassability(wc)) {
                                                            adjWall = true;
                                                            break;
                                                        }
                                                    } catch (GameActionException e) {
                                                        // ignore
                                                    }
                                                }
                                                if (adjWall) {
                                                    int dist = Math.abs(dx) + Math.abs(dy);
                                                    if (dist < bestDist) {
                                                        bestDist = dist;
                                                        best = cand;
                                                    }
                                                }
                                            }
                                        }
                                        if (best != null) {
                                            Direction toTarget = rc.getLocation().directionTo(best);
                                            if (toTarget != null && toTarget != Direction.CENTER) {
                                                if (rc.getDirection() != toTarget && rc.canTurn(toTarget)) {
                                                    rc.turn(toTarget);
                                                } else if (rc.canMove(toTarget)) {
                                                    rc.move(toTarget);
                                                } else if (rc.canMoveForward()) {
                                                    rc.moveForward();
                                                }
                                            }
                                        } else {
                                            // fallback wander
                                            if (rc.canMoveForward()) {
                                                rc.moveForward();
                                            } else if (rc.canTurn()) {
                                                rc.turn(directions[rng.nextInt(directions.length)]);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (GameActionException e) {
                            // If any game action fails here, ignore and continue main loop
                        }
                    }
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
}
