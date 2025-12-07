package battlecode.common;

/**
 * GameConstants defines constants that affect gameplay.
 */
@SuppressWarnings("unused")
public class GameConstants {

    /**
     * The current spec version the server compiles with.
     */
    public static final String SPEC_VERSION = "1";

    // *********************************
    // ****** MAP CONSTANTS ************
    // *********************************

    /** The minimum possible map height. */
    public static final int MAP_MIN_HEIGHT = 20;

    /** The maximum possible map height. */
    public static final int MAP_MAX_HEIGHT = 60;

    /** The minimum possible map width. */
    public static final int MAP_MIN_WIDTH = 20;

    /** The maximum possible map width. */
    public static final int MAP_MAX_WIDTH = 60;

    /** The maximum percentage of the map that can be walls */
    public static final int MAX_WALL_PERCENTAGE = 20;

    // *********************************
    // ****** GAME PARAMETERS **********
    // *********************************

    /** The default game seed. **/
    public static final int GAME_DEFAULT_SEED = 6370;

    /** The maximum number of rounds in a game. **/
    public static final int GAME_MAX_NUMBER_OF_ROUNDS = 2000;

    /** The maximum number of bytecodes a robot is allowed to use in one turn */
    public static final int ROBOT_BYTECODE_LIMIT = 17500;

    /** The maximum number of bytecodes a tower is allowed to use in one turn */
    public static final int TOWER_BYTECODE_LIMIT = 20000;

    /**
     * The maximum length of indicator strings that a player can associate with a
     * robot.
     */
    public static final int INDICATOR_STRING_MAX_LENGTH = 256;

    /** The maximum length of a label to add to the timeline. */
    public static final int TIMELINE_LABEL_MAX_LENGTH = 64;

    /** The bytecode penalty that is imposed each time an exception is thrown. */
    public static final int EXCEPTION_BYTECODE_PENALTY = 500;

    /** The amount of money each team starts with. */
    public static final int INITIAL_TEAM_MONEY = 2500; // TODO need to specify!

    /** The maximum number of rat kings that a team can have. */
    public static final int MAX_NUMBER_OF_RAT_KINGS = 5; // TODO need to specify!

    /** 
     * The maximum execution time that can be spent on a team in one match. If the total time spent executing a team's bots
     * exceeds this limit, the team will immediately lose the game. Execution time is measured in ns.
     */
    public static final long MAX_TEAM_EXECUTION_TIME = 1200000000000L;

    // *********************************
    // ****** GAME MECHANICS ***********
    // *********************************

    /** The number of rat kings a player starts with. */
    public static final int NUMBER_INITIAL_RAT_KINGS = 1;

    /** The maximum distance for marking a map location or removing a marker */
    public static final int MARK_RADIUS_SQUARED = 2;

    /** The maximum distance for transferring cheese to an allied rat king or dropping it on the ground */
    public static final int CHEESE_DROP_RADIUS_SQUARED = 2;

    /** The maximum number of traps a team can have at a given time */
    public static final int MAX_TRAP_COUNT = 25;

    /** The maximum distance from a rat king for building robots */
    public static final int BUILD_ROBOT_RADIUS_SQUARED = 4;

    /** The maximum distance from a robot for building traps or dirt */
    public static final int BUILD_DISTANCE_SQUARED = 9;

    /** The maximum amount of bytes that can be encoded in a message */
    public static final int MAX_MESSAGE_BYTES = 4; // TODO need to spec messages as a whole!

    /** The maximum squared radius a robot can send a message to */
    public static final int MESSAGE_RADIUS_SQUARED = 20; // TODO need to spec messages as a whole!

    /** The maxmimum squared radius a tower can broadcast a message */
    public static final int BROADCAST_RADIUS_SQUARED = 80; // TODO need to spec messages as a whole!

    /** The maximum number of rounds a message will exist for */
    public static final int MESSAGE_ROUND_DURATION = 5; // TODO need to spec messages as a whole!

    /** The maximum number of messages a robot can send per turn */
    public static final int MAX_MESSAGES_SENT_ROBOT = 1; // TODO need to spec messages as a whole!

    /** The maximum number of messages a tower can send per turn */
    public static final int MAX_MESSAGES_SENT_TOWER = 20;

    /** A robot takes this much damage every time it ends a turn with 0 paint */
    public static final int NO_PAINT_DAMAGE = 20;

    /** The damage a thrown rat takes upon hitting the ground or a target */
    public static final int THROW_DAMAGE = 20;

    /** The damage a thrown rat takes per tile it impacts early (i.e. rats that hit a wall after 1 turn take 45 damage) */
    public static final int THROW_DAMAGE_PER_TURN = 5;

    /** The damage a robot takes after being scratched by a cat */
    public static final int CAT_SCRATCH_DAMAGE = 50;

    /** The minimum gap between an enemy robot's health and our own before we can grab it from all angles */
    public static final int HEALTH_GRAB_THRESHOLD = 0;

    /** The area effected by the splasher's attack. Within this radius, empty tiles are painted and towers are damaged */
    public static final int SPLASHER_ATTACK_AOE_RADIUS_SQUARED = 4;
    public static final int MAX_MESSAGES_SENT_TOWER = 20; // TODO need to spec messages as a whole!

    /** The area effected by the cat's attack. */
    public static final int CAT_ATTACK_AOE_RADIUS_SQUARED = 4; // TODO need to specify!

    /** The cheese cost to dig up a tile of dirt */
    public static final int DIG_DIRT_CHEESE_COST = 10;

    /** The cheese cost to place a tile of dirt */
    public static final int PLACE_DIRT_CHEESE_COST = 20;

    // *********************************
    // ****** COOLDOWNS ****************
    // *********************************

    /** If the amount of cooldown is at least this value, a robot cannot act. */
    public static final int COOLDOWN_LIMIT = 10;

    /** The number of cooldown turns reduced per turn. */
    public static final int COOLDOWNS_PER_TURN = 10;

    /**
     * The amount added to the movement cooldown counter when moving
     */
     public static final int MOVEMENT_COOLDOWN = 10;

    /**
     * The amount added to the movement cooldown counter when a king moves
     */
     public static final int RAT_KING_MOVEMENT_COOLDOWN = 100; // TODO need to specify!

    /**
     * The amount added to the movement cooldown counter when a king moves
     */
     public static final int DRAGGING_MOVEMENT_COOLDOWN = 15; // TODO need to specify!

    /**
     * The amount added to the action cooldown counter after a king builds a robot
     */
     public static final int BUILD_ROBOT_COOLDOWN = 10;

    /** The amount added to the action cooldown counter after dropping/transferring cheese */
    public static final int CHEESE_TRANSFER_COOLDOWN = 10;

    /** The amount added to the action cooldown counter after digging out a tile of dirt */
    public static final int DIG_COOLDOWN = 25; // TODO need to specify!

    /** The amount added to the action cooldown counter after building a tile of dirt */
    public static final int BUILD_COOLDOWN = 35; // TODO need to specify!

    /** The total time a rat can travel for while thrown (rats are stunned while thrown) */
    public static final int THROW_DURATION = 40;

    /** The total time a rat is stunned after hitting the ground or a target */
    public static final int THROW_STUN_DURATION = 30;

}
