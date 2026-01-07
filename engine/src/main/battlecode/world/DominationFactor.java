package battlecode.world;

/**
 * Determines roughly by how much the winning team won.
 */
public enum DominationFactor {
    /**
     * Win by killing all opponent rat kings.
     */
    KILL_ALL_RAT_KINGS,
    /**
     * Win by having more points at the end of the game.
     */
    MORE_POINTS,
    /**
     * Win by having more rats alive (tiebreak 1).
     */
    MORE_ROBOTS_ALIVE,
    /**
     * Win by having more cheese at the end of the game (tiebreak 2).
     */
    MORE_CHEESE,
    /**
     * Win by coinflip (tiebreak 3).
     */
    WON_BY_DUBIOUS_REASONS,
    /**
     * Win because the other team resigns.
     */
    RESIGNATION;
}
