package battlecode.common;

public enum UnitType {
    // health, size, speed, visionRadius, actionCooldown
    RAT(10, 1, 5, 250, 0),
    RAT_KING(50, 3, 1, 150, -1),
    CAT(500,2,10,0,0);

    // amount of health robot initially starts with
    public final int health;

    // robot's size as a length (so number of squares occupied is size^2)
    public final int size;

    // robot's movement speed
    public final int speed;

    // robot's vision radius
    public final int visionRadius;

    // number of turns before unit can act again
    public final int actionCooldown;

    public boolean isRobotType(){
        return this == RAT || this == RAT_KING || this == CAT;
    }

    public boolean isRatType(){
        return this == RAT || this == RAT_KING;
    }

    UnitType(int health, int size, int speed, int visionRadius, int actionCooldown) {
        this.health = health;
        this.size = size;
        this.speed = speed;
        this.visionRadius = visionRadius;
        this.actionCooldown = actionCooldown;
    }

    // Getters 
    // (we didn't have these before so unclear how useful they are but
    // I'm adding them for completeness)
    public int getHealth() { return health; }
    public int getSize() { return size; }
    public int getSpeed() { return speed; }
    public int getVisionRadius() { return visionRadius; }
    public int getActionCooldown() { return actionCooldown; }
}
