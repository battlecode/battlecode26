package battlecode.common;

/**
 * Enumerates possible traps that can be built.
 */

public enum TrapType {

    /**
     * Traps enemy rats
     */
    RAT_TRAP(20, 50, 30, 15, 25, 2),

    /**
     * Traps the cat
     */
    CAT_TRAP(10, 100, 20, 10, 10, 2),

    /**
     * No trap
     */
    NONE(0, 0, 0, 0, 0, 0);

    /**
     * Crumbs cost of each trap
     */
    public final int buildCost;

    /**
     * The damage done if trap triggered by opponent triggering the trap
     */
    public final int damage;

    /**
     * How many turn stun lasts after entering
     */
    public final int stunTime;

    /*
     * action cooldown for trap placement
     */
    public final int actionCooldown;

    /**
     * Maximum number of this trap type that a team can have active at once
     */
    public final int maxCount;

    /**
     * The radius within which the trap is triggered
     */
    public final int triggerRadiusSquared;
    

    TrapType(int buildCost, int damage, int stunTime, int actionCooldown, int maxCount, int triggerRadiusSquared) {
        this.buildCost = buildCost;
        this.damage = damage;
        this.stunTime = stunTime;
        this.actionCooldown = actionCooldown;
        this.maxCount = maxCount;
        this.triggerRadiusSquared = triggerRadiusSquared;
    }
}
