package battlecode.common;

/**
 * Enumerates possible traps that can be built.
 */

public enum TrapType {

    // build cost, damage from activation, stun time from activation, global trap
    // limit, action cooldown

    /**
     * Traps enemy rats
     */
    RATTRAP(5, 5, 2, 25, 5),

    /**
     * Traps the cat
     */
    CATTRAP(10, 2, 9, 5, 10),

    NONE(100, 5, 2, 0, 0);

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

    /**
     * How many traps of this type can be on the map at the same time
     */
    public final int trapLimit;

    /*
     * action cooldown for trap placement
     */
    public final int actionCooldown;

    TrapType(int buildCost, int damage, int stunTime, int trapLimit, int actionCooldown) {
        this.buildCost = buildCost;
        this.damage = damage;
        this.stunTime = stunTime;
        this.trapLimit = trapLimit;
        this.actionCooldown = actionCooldown;
    }
}