package battlecode.common;

public enum UnitType {
    // health, size, visionConeRadiusSquared, visionConeAngle, actionCooldown, movementCooldown, bytecodeLimit
    BABY_RAT(100, 1, 20, 90, 10, 10, 17500),
    RAT_KING(600, 3, 25, 360, 10, 40, 20000),
    CAT(4000, 2, 17, 180, 30, 20, 17500);

    // amount of health robot initially starts with
    public final int health;

    // robot's size as a length (so number of squares occupied is size^2)
    public final int size;

    // robot's vision radius
    public final int visionConeRadiusSquared;

    // robot's vision cone angle (in degrees)
    public final int visionConeAngle;

    // amount action cooldown gets incremented for taking an action (i.e. attacking)
    public final int actionCooldown;

    // amount movement cooldown gets incremented for moving
    public final int movementCooldown;

    // robot's bytecode limit
    public final int bytecodeLimit;

    public boolean usesBottomLeftLocationForDistance() {
        return this.size % 2 == 0;
    }

    public boolean isRobotType() {
        return this == BABY_RAT || this == CAT || this == RAT_KING;
    }

    public boolean isThrowableType() {
        return this == BABY_RAT;
    }

    public boolean isThrowingType() {
        return this == BABY_RAT;
    }

    public boolean isBabyRatType() {
        return this == BABY_RAT;
    }

    public boolean isRatKingType() {
        return this == RAT_KING;
    }

    public boolean isCatType() {
        return this == CAT;
    }

    public MapLocation[] getAllTypeLocations(MapLocation center) {
        // return robot part locations in order of increasing x and y values, starting from bottom left corner
        MapLocation[] locs = new MapLocation[size * size];
        int c = 0;
        for (int i = -(size - 1) / 2; i <= size / 2; i++) {
            for (int j = -(size - 1) / 2; j <= size / 2; j++) {

                if (this.isCatType()) {
                    locs[c] = new MapLocation(center.x + i, center.y + j);
                } else {
                    locs[c] = new MapLocation(center.x + i, center.y - j);
                }

                c += 1;
            }
        }
        return locs;
    }

    UnitType(int health, int size, int visionConeRadiusSquared, int visionConeAngle, int actionCooldown,
            int movementCooldown, int bytecodeLimit) {
        this.health = health;
        this.size = size;
        this.visionConeRadiusSquared = visionConeRadiusSquared;
        this.visionConeAngle = visionConeAngle;
        this.actionCooldown = actionCooldown;
        this.movementCooldown = movementCooldown;
        this.bytecodeLimit = bytecodeLimit;
    }

    // Getters
    public int getHealth() {
        return health;
    }

    public int getSize() {
        return size;
    }

    public int getVisionRadiusSquared() {
        return visionConeRadiusSquared;
    }

    public int getVisionAngle() {
        return visionConeAngle;
    }

    public int getActionCooldown() {
        return actionCooldown;
    }

    public int getMovementCooldown() {
        return movementCooldown;
    }

    public int getBytecodeLimit() {
        return bytecodeLimit;
    }
}
