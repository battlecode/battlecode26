package battlecode.world;

import battlecode.common.Team;

/**
 * This class is used to hold information regarding team specific values such as
 * team names.
 */
public class TeamInfo {

    // private GameWorld gameWorld;
    private int[] globalCheese;
    private int[] dirtCounts;
    private int[] oldCheeseCounts;
    private int[] cheeseCollected;
    private int[] cheeseTransferred;
    private int[] numBabyRats;
    private int[] numRatKings;
    private int[] damageToCats;
    private int[] damageSuffered;
    private int[] points;

    /**
     * Create a new representation of TeamInfo
     *
     * @param gameWorld the gameWorld the teams exist in
     */
    public TeamInfo(GameWorld gameWorld) {
        // this.gameWorld = gameWorld;
        this.globalCheese = new int[2];
        this.dirtCounts = new int[2];
        this.oldCheeseCounts = new int[2];
        this.cheeseCollected = new int[2];
        this.cheeseTransferred = new int[2];
        this.numBabyRats = new int[2];
        this.damageToCats = new int[2];
        this.damageSuffered = new int[2];
        this.numRatKings = new int[2];
        this.points = new int[2];
    }

    // *********************************
    // ***** GETTER METHODS ************
    // *********************************

    /**
     * Get the amount of cheese.
     * 
     * @param team the team to query
     * @return the team's cheese count
     */

    public int getCheese(Team team) {
        return this.globalCheese[team.ordinal()];
    }

    /**
     * Get the amount of cheese collected.
     * 
     * @param team the team to query
     * @return the team's cheese count
     */

    public int getCheeseTransferred(Team team) {
        return this.cheeseTransferred[team.ordinal()];
    }

    /**
     * Get the amount of dirt.
     * 
     * @param team the team to query
     * @return the team's dirt count
     */
    public int getDirt(Team team) {
        return this.dirtCounts[team.ordinal()];
    }

    /**
     * Get the total number of rats belonging to a team
     * 
     * @param team the team to query
     * @return the number of rats the team has
     */
    public int getNumBabyRats(Team team) {
        return this.numBabyRats[team.ordinal()];
    }

    /**
     * Get the total number of rat kings belonging to a team
     * 
     * @param team the team to query
     * @return the number of rats the team has
     */
    public int getNumRatKings(Team team) {
        return this.numRatKings[team.ordinal()];
    }

    /**
     * Get how much damage to cats a team has done
     * 
     * @param team the team to query
     * @return the team's damage done to cats
     */
    public int getDamageToCats(Team team) {
        return this.damageToCats[team.ordinal()];
    }

    /**
     * Get how much damage this team suffered
     * 
     * @param team the team to query
     * @return the team's total amount of health lost
     */
    public int getDamageSuffered(Team team) {
        return this.damageSuffered[team.ordinal()];
    }

    /**
     * Get the amount of points belonging to a team
     * 
     * @param team the team to query
     * @return the number of points the team has
     */
    public int getPoints(Team team) {
        return this.points[team.ordinal()];
    }

    // *********************************
    // ***** UPDATE METHODS ************
    // *********************************


    /**
     * Change the total number of baby rats belonging to a team
     * 
     * @param team the team to change
     */
    public void addBabyRats(int num, Team team) {
        this.numBabyRats[team.ordinal()] += num;
    }

    /**
     * Change the total number of rat kings belonging to a team
     * 
     * @param team the team to change
     */
    public void addRatKings(int num, Team team) {
        this.numRatKings[team.ordinal()] += num;
    }

    /**
     * Add to the amount of cheese. If amount is negative, subtract from cheese
     * instead.
     * 
     * @param team   the team to query
     * @param amount the change in the cheese count
     * @throws IllegalArgumentException if the resulting amount of cheese is
     *                                  negative
     */
    public void addCheese(Team team, int amount) throws IllegalArgumentException {
        if (this.globalCheese[team.ordinal()] + amount < 0) {
            throw new IllegalArgumentException("Invalid cheese change");
        }
        this.globalCheese[team.ordinal()] += amount;
    }

    /**
     * Add to the amount of cheese collected.
     * 
     * @param team   the team to query
     * @param amount    cheese collected
     */
    public void addCheeseCollected(Team team, int amount) {
        this.cheeseCollected[team.ordinal()] += amount;
    }

    /**
     * Add to the amount of cheese transferred to rat king.
     * 
     * @param team   the team to query
     * @param amount    cheese transfeered
     */
    public void addCheeseTransferred(Team team, int amount) {
        this.cheeseTransferred[team.ordinal()] += amount;
    }

    /**
     * Add to the damage done to cats for a team.
     * 
     * @param team   team to attribute damage to
     * @param amount the change in the amount of damage done to cats
     */
    public void addDamageToCats(Team team, int amount) {
        this.damageToCats[team.ordinal()] += amount;
    }

    /**
     * Add to the damage done to other team's rats for a team.
     * 
     * @param team   team to attribute damage to
     * @param amount the change in the amount of damage done to enemy rats
     */
    public void addDamageSuffered(Team team, int amount) {
        this.damageSuffered[team.ordinal()] += amount;
    }

    /**
     * Add points to teams.
     * 
     * @param team   team to add points to
     * @param amount the change in the amount of points
     */
    public void addPoints(Team team, int amount) {
        this.points[team.ordinal()] += amount;
    }

    /**
     * Update the amount of dirt.
     * 
     * @param team    the team to query
     * @param isPlace whether dirt is being placed (true) or removed (false)
     */
    public void updateDirt(Team team, boolean isPlace) {
        if (team.ordinal() == 2) {
            return; // cat dig
        }

        if (isPlace) {
            this.dirtCounts[team.ordinal()] -= 1;
        } else {
            this.dirtCounts[team.ordinal()] += 1;
        }
    }

    public int getRoundCheeseChange(Team team) {
        return this.globalCheese[team.ordinal()] - this.oldCheeseCounts[team.ordinal()];
    }

    public void processEndOfRound() {
        this.oldCheeseCounts[0] = this.globalCheese[0];
        this.oldCheeseCounts[1] = this.globalCheese[1];
    }

}
