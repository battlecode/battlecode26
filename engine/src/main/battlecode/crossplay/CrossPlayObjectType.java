package battlecode.crossplay;

public enum CrossPlayObjectType {
    INVALID,
    CALL,
    NULL,
    INTEGER,
    STRING,
    BOOLEAN,
    DOUBLE,
    ARRAY,
    DIRECTION,
    MAP_LOCATION,
    MESSAGE,
    ROBOT_CONTROLLER,
    ROBOT_INFO,
    TEAM,
    THROWN_GAME_ACTION_EXCEPTION,
    // TODO add more types
    ;

    public static final CrossPlayObjectType[] values = values();
}
