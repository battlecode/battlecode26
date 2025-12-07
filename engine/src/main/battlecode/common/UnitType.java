package battlecode.common;

public enum UnitType {
    // health, size, speed, visionRadius, actionCooldown
    RAT(10, 1, 5, 250, 90, 0),
    RAT_KING(50, 3, 1, 150, 360, -1),
    CAT(500,2,10,0,180, 0);

    // amount of health robot initially starts with
    public final int health;

    // robot's size as a length (so number of squares occupied is size^2)
    public final int size;

    // robot's movement speed
    public final int speed;

    // robot's vision radius
    public final int visionConeRadius;

    // robot's vision cone angle (in degrees)
    public final int visionConeAngle;

    // number of turns before unit can act again
    public final int actionCooldown;

    public boolean usesTopRightLocationForDistance(){
        return this.size % 2 == 0;
    }

    public boolean isRobotType(){
        return this == RAT || this == CAT || this == KING_RAT;
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
        MapLocation[] locs = new MapLocation[size * size];
        int c = 0;
        for (int i = - (size-1) / 2; i <= size / 2; i++){
            for (int j = - (size-1) / 2; j <= size / 2; j++){
                locs[c] = new MapLocation(center.x + i, center.y + j);
                c += 1;
            }
        }
        return locs;
    }
  
    UnitType(int health, int size, int speed, int visionConeRadius, int visionConeAngle, int actionCooldown) {
        this.health = health;
        this.size = size;
        this.speed = speed;
        this.visionConeRadius = visionConeRadius;
        this.visionConeAngle = visionConeAngle;
        this.actionCooldown = actionCooldown;
    }

    // Getters 
    // (we didn't have these before so unclear how useful they are but
    // I'm adding them for completeness)
    public int getHealth() { return health; }
    public int getSize() { return size; }
    public int getSpeed() { return speed; }
    public int getVisionRadius() { return visionConeRadius; }
    public int getVisionAngle() { return visionConeAngle; }
    public int getActionCooldown() { return actionCooldown; }
}
