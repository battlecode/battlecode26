package battlecode.common;

public enum UnitType {
    // health, size, speed, visionRadius, actionCooldown
    RAT(20, 1, 5, 20, 90, 0, 17500),
    RAT_KING(150, 3, 1, 29, 90, -1, 20000),
    CAT(1000,2,10,37,180, 0, -1);

    // amount of health robot initially starts with
    public final int health;

    // robot's size as a length (so number of squares occupied is size^2)
    public final int size;

    // robot's movement speed
    public final int speed;

    // robot's vision radius
    public final int visionConeRadiusSquared;

    // robot's vision cone angle (in degrees)
    public final int visionConeAngle;

    // amount action cooldown gets incremented for taking an action
    public final int actionCooldown;

    // robot's bytecode limit
    public final int bytecodeLimit;

    public boolean usesTopRightLocationForDistance(){
        return this.size % 2 == 0;
    }

    public boolean isRobotType(){
        return this == RAT || this == CAT || this == RAT_KING;
    }

    public boolean isThrowableType(){
        return this == RAT;
    }

    public boolean isThrowingType(){
        return this == RAT;
    }

    public boolean isRatType(){
        return this == RAT;
    }

    public boolean isRatKingType() {
        return this == RAT_KING;
    }

    public boolean isCatType() {
        return this == CAT;
    }

    public MapLocation[] getAllLocations(MapLocation center){
        // return in CCW order starting from top left
        MapLocation[] locs = new MapLocation[size * size];
        int c = 0;
        for (int i = - (size-1) / 2; i <= size / 2; i++){
            for (int j = - (size-1) / 2; j <= size / 2; j++){
                locs[c] = new MapLocation(center.x + i, center.y - j);
                c += 1;
            }
        }
        return locs;
    }
  
    UnitType(int health, int size, int speed, int visionConeRadius, int visionConeAngle, int actionCooldown, int bytecodeLimit) {
        this.health = health;
        this.size = size;
        this.speed = speed;
        this.visionConeRadiusSquared = visionConeRadius;
        this.visionConeAngle = visionConeAngle;
        this.actionCooldown = actionCooldown;
        this.bytecodeLimit = bytecodeLimit;
    }

    // Getters 
    public int getHealth() { return health; }
    public int getSize() { return size; }
    public int getSpeed() { return speed; }
    public int getVisionRadiusSquared() { return visionConeRadiusSquared; }
    public int getVisionAngle() { return visionConeAngle; }
    public int getActionCooldown() { return actionCooldown; }
    public int getBytecodeLimit() {return bytecodeLimit; }
}
