package lectureplayer;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public class RobotPlayer {
    public static enum State {
        INITIALIZE,
        FIND_CHEESE,
        RETURN_TO_KING,
        BUILD_TRAPS,
        EXPLORE_AND_ATTACK,
        RETURN_TO_KING_THEN_EXPLORE,
    }

    public static Random rand = new Random(1092);

    public static State currentState = State.INITIALIZE;

    public static int numRatsSpawned = 0;
    public static int turnsSinceCarry = 1000;

    public static Direction[] directions = Direction.values();

    public static MapLocation mineLoc = null;
    public static int numMines = 0;
    public static ArrayList<Integer> mineLocs = new ArrayList<>();

    public static boolean exploreWhenFindingCheese = false;
    public static MapLocation targetCheeseMineLoc = null;

    public static enum SqueakType {
        INVALID,
        ENEMY_RAT_KING,
        ENEMY_COUNT,
        CHEESE_MINE,
        CAT_FOUND,
    }

    public static SqueakType[] squeakTypes = SqueakType.values();

    public static void run(RobotController rc) {
        while (true) {
            try {
                if (rc.getType().isRatKingType()) {
                    runRatKing(rc);
                } else {
                    turnsSinceCarry++;

                    switch (currentState) {
                        case INITIALIZE:
                            if (rc.getRoundNum() < 30 || rc.getCurrentRatCost() <= 10) {
                                currentState = State.FIND_CHEESE;
                                exploreWhenFindingCheese = rand.nextBoolean() && rand.nextBoolean();
                            } else {
                                currentState = State.EXPLORE_AND_ATTACK;
                            }

                            break;
                        case FIND_CHEESE:
                            runFindCheese(rc);
                            break;
                        case RETURN_TO_KING:
                            runReturnToKing(rc);
                            break;
                        case BUILD_TRAPS:
                            runBuildTraps(rc);
                            break;
                        case EXPLORE_AND_ATTACK:
                            runExploreAndAttack(rc);
                            break;
                        case RETURN_TO_KING_THEN_EXPLORE:
                            runReturnToKing(rc);

                            if (currentState == State.FIND_CHEESE) {
                                currentState = State.EXPLORE_AND_ATTACK;
                            }
                    }
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException in RobotPlayer:");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception in RobotPlayer:");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void moveRandom(RobotController rc) throws GameActionException {
        MapLocation forwardLoc = rc.adjacentLocation(rc.getDirection());

        if (rc.canRemoveDirt(forwardLoc)) {
            rc.removeDirt(forwardLoc);
        }

        if (rc.canMoveForward()) {
            rc.moveForward();
        } else {
            Direction random = directions[rand.nextInt(directions.length)];

            if (rc.canTurn(random)) {
                rc.turn(random);
            }
        }
    }

    public static void runRatKing(RobotController rc) throws GameActionException {
        int currentCost = rc.getCurrentRatCost();

        MapLocation[] potentialSpawnLocations = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 8);
        boolean spawn = currentCost <= 10 || rc.getAllCheese() > currentCost + 2500;

        for (MapLocation loc : potentialSpawnLocations) {
            if (spawn && rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                numRatsSpawned++;
                break;
            }

            if (rc.canPickUpCheese(loc)) {
                rc.pickUpCheese(loc);
                break;
            }
        }

        Message[] squeaks = rc.readSqueaks(rc.getRoundNum());

        for (Message msg : squeaks) {
            int rawSqueak = msg.getBytes();

            if (getSqueakType(rawSqueak) != SqueakType.CHEESE_MINE) {
                continue;
            }

            int encodedLoc = getSqueakValue(rawSqueak);

            if (mineLocs.contains(encodedLoc)) {
                continue;
            }

            mineLocs.add(encodedLoc);
            int firstInt = getFirstInt(encodedLoc);
            int lastInt = getLastInt(encodedLoc);

            rc.writeSharedArray(2 * numMines + 2, firstInt);
            rc.writeSharedArray(2 * numMines + 3, lastInt);
            System.out.println("Writing to shared array: " + firstInt + ", " + lastInt);
            System.out.println("Cheese mine located at: " + getX(encodedLoc) + ", " + getY(encodedLoc));

            numMines++;
        }

        moveRandom(rc);

        // TODO make more efficient and expand communication in the communication lecture
        rc.writeSharedArray(0, rc.getLocation().x);
        rc.writeSharedArray(1, rc.getLocation().y);
    }

    public static void runFindCheese(RobotController rc) throws GameActionException {
        if (!exploreWhenFindingCheese && numMines == 0) {
            exploreWhenFindingCheese = true;
        }

        if (targetCheeseMineLoc == null && !exploreWhenFindingCheese) {
            int cheeseMineIndex = rand.nextInt(numMines);
            int x = rc.readSharedArray(2 * cheeseMineIndex + 2);
            int y = rc.readSharedArray(2 * cheeseMineIndex + 3);
            int encodedLoc = 1024 * y + x;
            targetCheeseMineLoc = new MapLocation(getX(encodedLoc), getY(encodedLoc));
        }

        // search for cheese
        MapInfo[] nearbyInfos = rc.senseNearbyMapInfos();

        for (MapInfo info : nearbyInfos) {
            if (info.getCheeseAmount() > 0) {
                Direction toCheese = rc.getLocation().directionTo(info.getMapLocation());

                if (rc.canTurn(toCheese)) {
                    rc.turn(toCheese);
                    break;
                }
            } else if (info.hasCheeseMine()) {
                mineLoc = info.getMapLocation();
                System.out.println("Found cheese mine at " + mineLoc);
            }
        }

        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir);
            
            if (rc.canPickUpCheese(loc)) {
                rc.pickUpCheese(loc);

                if (rc.getRawCheese() >= 10) {
                    currentState = State.RETURN_TO_KING;
                }
            }
        }

        if (exploreWhenFindingCheese) {
            rc.setIndicatorString("Exploring!");
            moveRandom(rc);
        } else if (targetCheeseMineLoc != null) {
            rc.setIndicatorString("Going to cheese mine at " + targetCheeseMineLoc);
            Direction toTarget = rc.getLocation().directionTo(targetCheeseMineLoc);
            MapLocation nextLoc = rc.getLocation().add(toTarget);

            if (rc.canTurn(toTarget)) {
                rc.turn(toTarget);
            }

            if (rc.canRemoveDirt(nextLoc)) {
                rc.removeDirt(nextLoc);
            }

            // TODO replace with pathfinding for the pathfinding lecture
            if (rc.canMove(toTarget)) {
                rc.move(toTarget);
            }

            targetCheeseMineLoc = null;
        }
    }

    public static void runReturnToKing(RobotController rc) throws GameActionException {
        MapLocation kingLoc = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
        Direction toKing = rc.getLocation().directionTo(kingLoc);
        MapLocation nextLoc = rc.getLocation().add(toKing);

        if (rc.canTurn(toKing)) {
            rc.turn(toKing);
        }

        if (rc.canRemoveDirt(nextLoc)) {
            rc.removeDirt(nextLoc);
        }

        // TODO replace with pathfinding for the pathfinding lecture
        if (rc.canMove(toKing)) {
            rc.move(toKing);
        }

        int rawCheese = rc.getRawCheese();

        if (rawCheese == 0) {
            currentState = State.FIND_CHEESE;
            exploreWhenFindingCheese = rand.nextBoolean() && rand.nextBoolean();
        }
        
        if (rc.canSenseLocation(kingLoc)) {
            if (kingLoc.distanceSquaredTo(rc.getLocation()) <= 16 && mineLoc != null) {
                rc.squeak(getSqueak(SqueakType.CHEESE_MINE, toInteger(mineLoc)));
            }

            RobotInfo[] kingLocations = rc.senseNearbyRobots(kingLoc, 8, rc.getTeam());

            for (RobotInfo robotInfo : kingLocations) {
                if (robotInfo.getType().isRatKingType()) {
                    MapLocation actualKingLoc = robotInfo.getLocation();

                    if (rc.canTransferCheese(actualKingLoc, rawCheese)) {
                        System.out.println("Transferred " + rawCheese + " cheese to king at " + kingLoc + ": I'm at " + rc.getLocation());
                        rc.transferCheese(actualKingLoc, rawCheese);
                        currentState = State.FIND_CHEESE;
                        exploreWhenFindingCheese = rand.nextBoolean() && rand.nextBoolean();
                    }

                    break;
                }
            }
        }
    }

    public static void runBuildTraps(RobotController rc) throws GameActionException {
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir);
            boolean catTraps = rand.nextBoolean();
            
            if (catTraps && rc.canPlaceCatTrap(loc)) {
                System.out.println("Built cat trap at " + loc);
                rc.placeCatTrap(loc);
            } else if (rc.canPlaceRatTrap(loc)) {
                System.out.println("Built rat trap at " + loc);
                rc.placeRatTrap(loc);
            }
        }

        if (rand.nextDouble() < 0.1) {
            currentState = State.EXPLORE_AND_ATTACK;
        }

        moveRandom(rc);
    }

    public static void runExploreAndAttack(RobotController rc) throws GameActionException {
        Message[] squeaks = rc.readSqueaks(rc.getRoundNum());

        for (Message msg : squeaks) {
            int rawSqueak = msg.getBytes();

            if (getSqueakType(rawSqueak) != SqueakType.CAT_FOUND) {
                continue;
            }

            int dirOrdinal = getSqueakValue(rawSqueak);
            Direction toCat = directions[dirOrdinal];
            Direction away = toCat.opposite();

            if (rc.canTurn(away)) {
                rc.turn(away);
                break;
            }

            if (rc.canRemoveDirt(rc.getLocation().add(away))) {
                rc.removeDirt(rc.getLocation().add(away));
            }

            if (rc.canMove(away)) {
                rc.move(away);
                break;
            }
        }

        moveRandom(rc);

        if (rc.canThrowRat() && turnsSinceCarry >= 3) {
            rc.throwRat();
        }

        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir);

            if (rc.canCarryRat(loc)) {
                rc.carryRat(loc);
                turnsSinceCarry = 0;
            }

            if (rc.canAttack(loc)) {
                rc.attack(loc);
            }
        }

        if (rand.nextDouble() < 0.1) {
            currentState = State.BUILD_TRAPS;
        }

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(rc.getType().getVisionRadiusSquared(), rc.getTeam().opponent());
        RobotInfo[] nearbyCats = rc.senseNearbyRobots(rc.getType().getVisionRadiusSquared(), Team.NEUTRAL);

        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.getType().isRatKingType()) {
                // TODO found enemy rat king, message your own king
                currentState = State.RETURN_TO_KING_THEN_EXPLORE;
            }
        }

        int numEnemies = nearbyEnemies.length;
        if (numEnemies > 0) {
            rc.setIndicatorString("Nearby enemies: " + numEnemies);
            rc.squeak(getSqueak(SqueakType.ENEMY_COUNT, numEnemies));
        }

        if (nearbyCats.length > 0) {
            // if distance squared to cat >= 17
            if (rc.getLocation().distanceSquaredTo(nearbyCats[0].getLocation()) >= 17) {
                rc.setIndicatorString("Found a cat at " + nearbyCats[0].getLocation());
                Direction toCat = rc.getLocation().directionTo(nearbyCats[0].getLocation());
                rc.squeak(getSqueak(SqueakType.CAT_FOUND, toCat.ordinal()));
            } else {
                rc.setIndicatorString("Cat is too close! Running away!");
                Direction away = rc.getLocation().directionTo(nearbyCats[0].getLocation()).opposite();
                if (rc.canTurn(away)) {
                    rc.turn(away);
                }

                if (rc.canRemoveDirt(rc.getLocation().add(away))) {
                    rc.removeDirt(rc.getLocation().add(away));
                }

                if (rc.canMove(away)) {
                    rc.move(away);
                }
            }
        }
    }

    public static int toInteger(MapLocation loc) {
        // loc.x is between 0 and 60
        // loc.y is between 0 and 60
        // ==> both can fit in 6 bits each
        return (loc.x << 6) | loc.y;
    }

    public static int getFirstInt(int loc) {
        // extract 10 smallest place value bits from toInteger(loc)
        return loc % 1024;
    }

    public static int getLastInt(int loc) {
        // extract bits with place values >= 2^10 from toInteger(loc)
        return loc >> 10;
    }

    public static int getX(int encodedLoc) {
        return encodedLoc >> 6;
    }

    public static int getY(int encodedLoc) {
        return encodedLoc % 64;
    }

    public static int getSqueak(SqueakType type, int value) {
        switch (type) {
            case ENEMY_RAT_KING:
                return (1 << 12) | value;
            case ENEMY_COUNT:
                return (2 << 12) | value;
            case CHEESE_MINE:
                return (3 << 12) | value;
            case CAT_FOUND:
                return (4 << 12) | value;
            default:
                return value;
        }
    }

    public static SqueakType getSqueakType(int rawSqueak) {
        return squeakTypes[rawSqueak >> 12];
    }

    public static int getSqueakValue(int rawSqueak) {
        return rawSqueak % 4096;
    }
}
