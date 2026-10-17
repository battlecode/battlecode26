package battlecode.world;

import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

public class CheeseMine {
    private int id;
    private CheeseMine pair;
    private MapLocation loc;
    private int last_spawn_round;

    public CheeseMine(MapLocation loc, int id, CheeseMine pair) {
        this.loc = loc;
        this.last_spawn_round = 0;
        this.pair = pair;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public CheeseMine getPair() {
        return pair;
    }

    public void setPair(CheeseMine mine) {
        this.pair = mine;
    }

    public MapLocation getLocation() {
        return this.loc;
    }

    public String toString() {
        return "CheesMine{" + "loc= " + loc + ", paired at= " + pair.loc + "}";
    }

    /**
     * Calculates the probability of spawning cheese this round based on when
     * cheese was last spawned.
     * 
     * @param currentRound The current round number
     * @return The probability of spawning cheese this round
     */
    public double generationProbability(int currentRound) {
        int roundsSinceLastSpawn = currentRound - last_spawn_round;
        double prob = 1 - (double) Math.pow(1 - GameConstants.CHEESE_MINE_SPAWN_PROBABILITY,
                roundsSinceLastSpawn);
        return prob;
    }

    public void setLastRound(int currentRound) {
        this.last_spawn_round = currentRound;
    }
}
