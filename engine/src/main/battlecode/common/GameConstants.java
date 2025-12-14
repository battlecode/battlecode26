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

    /** The minimum distance between cheese mines on the map */
    public static final int MIN_CHEESE_MINE_SPACING_SQUARED = 25;

    /** The maximum percentage of the map that can be dirt */
    public static final int MAX_DIRT_PERCENTAGE = 20;

    /** The maximum percentage of the map that can be walls */
    public static final int MAX_WALL_PERCENTAGE = 10; // TODO remove if not using walls

    // *********************************
    // ****** GAME PARAMETERS **********
    // *********************************

    /** The default game seed. **/
    public static final int GAME_DEFAULT_SEED = 6370;

    /** The maximum number of rounds in a game. **/
    public static final int GAME_MAX_NUMBER_OF_ROUNDS = 2000;

    /**
     * The maximum length of indicator strings that a player can associate with a
     * robot.
     */
    public static final int INDICATOR_STRING_MAX_LENGTH = 256;

    /** The maximum length of a label to add to the timeline. */
    public static final int TIMELINE_LABEL_MAX_LENGTH = 64;

    /** The bytecode penalty that is imposed each time an exception is thrown. */
    public static final int EXCEPTION_BYTECODE_PENALTY = 500;

    /** The amount of cheese each team starts with. */
    public static final int INITIAL_TEAM_CHEESE = 2500; 

    /** The maximum number of rat kings that a team can have. */
    public static final int MAX_NUMBER_OF_RAT_KINGS = 5; // TODO need to specify!

    /**
     * The maximum execution time that can be spent on a team in one match. If the
     * total time spent executing a team's bots
     * exceeds this limit, the team will immediately lose the game. Execution time
     * is measured in ns.
     */
    public static final long MAX_TEAM_EXECUTION_TIME = 1200000000000L;

    // *********************************
    // ****** GAME MECHANICS ***********
    // *********************************

    /** The amount of cheese the ratking consumes each round. */
    public static final int RATKING_CHEESE_CONSUMPTION = 10;

    /** The amount of health the ratking loses by not eating cheese. */
    public static final int RATKING_HEALTH_LOSS = 10;

    /** Probability parameter for cheese spawn at a mine. **/
    public static final float CHEESE_MINE_SPAWN_PROBABILITY = 0.15f;

    /** Cheese will spawn within a [-radius, radius] square of the cheese mine **/
    public static final int SQ_CHEESE_SPAWN_RADIUS = 5;

    /** How much cheese each mine spawns at once */
    public static final int CHEESE_SPAWN_AMOUNT = 2;

    /** The number of rat kings a player starts with. */
    public static final int NUMBER_INITIAL_RAT_KINGS = 1;

    /** The maximum distance for transferring cheese to an allied rat king or dropping it on the ground */
    public static final int CHEESE_DROP_RADIUS_SQUARED = 2;

    /** The maximum number of traps a team can have at a given time */
    public static final int MAX_TRAP_COUNT = 25;

    /** The maximum distance from a rat king for building robots */
    public static final int BUILD_ROBOT_RADIUS_SQUARED = 4;

    /** The base cheese cost for spawning a rat */
    public static final int BUILD_ROBOT_BASE_COST = 10;

    /**
     * The amount by which the cost to spawn a rat increases by for every
     * NUM_ROBOTS_FOR_COST_INCREASE allied rats
     */
    public static final int BUILD_ROBOT_COST_INCREASE = 10;

    /**
     * The number of allied rats needed to increase the base cost of a rat by
     * BUILD_ROBOT_COST_INCREASE
     */
    public static final int NUM_ROBOTS_FOR_COST_INCREASE = 4;

    /** The maximum distance from a robot for building traps or dirt */
    public static final int BUILD_DISTANCE_SQUARED = 2;

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

    /** The maximum squared radius a robot can squeak to */
    public static final int SQUEAK_RADIUS_SQUARED = 16;

    /** The damage a thrown rat takes upon hitting the ground or a target */
    public static final int THROW_DAMAGE = 20;

    /**
     * The damage a thrown rat takes per tile it impacts early (i.e. rats that hit a
     * wall after 1 turn take 45 damage)
     */
    public static final int THROW_DAMAGE_PER_TURN = 5;

    /** The damage a robot takes after being bitten by a rat */
    public static final int RAT_BITE_DAMAGE = 2;

    /** The damage a robot takes after being scratched by a cat */
    public static final int CAT_SCRATCH_DAMAGE = 50;

    /** The distance squared a cat can pounce to */
    public static final int CAT_POUNCE_MAX_DISTANCE_SQUARED = 9;

    /**
     * Percent damage a rat takes when a cat pounces to an adjacent location (eg. 50
     * = 50% damage)
     */
    public static final int CAT_POUNCE_ADJACENT_DAMAGE_PERCENT = 50;

    public static final int CAT_DIG_COOLDOWN = 30;

    /**
     * The minimum gap between an enemy robot's health and our own before we can
     * grab it from all angles
     */
    public static final int HEALTH_GRAB_THRESHOLD = 0;

    /** The area effected by the cat's attack. */
    public static final int CAT_ATTACK_AOE_RADIUS_SQUARED = 4; // TODO need to specify!

    /** The cheese cost for upgrading a rat into a rat king */
    public static final int RAT_KING_UPGRADE_CHEESE_COST = 50;
  
    /** The cheese cost to dig up a tile of dirt */
    public static final int DIG_DIRT_CHEESE_COST = 10;

    /** The cheese cost to place a tile of dirt */
    public static final int PLACE_DIRT_CHEESE_COST = 20;

    /** After this many turns, cats will begin moving if they haven't already. */
    public static final int CAT_GRACE_PERIOD = 100;

    // *********************************
    // ****** COMMUNICATION ************
    // *********************************

    /** The size of the shared array. */
    public static final int SHARED_ARRAY_SIZE = 64;

    /** The maximum value of an integer in the shared array and persistent array. */
    public static final int COMM_ARRAY_MAX_VALUE = 1023;

    /** The size of the persistent array. */
    public static final int PERSISTENT_ARRAY_SIZE = 5;

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

    /**
     * The amount added to the action cooldown counter after dropping/transferring
     * cheese
     */
    public static final int CHEESE_TRANSFER_COOLDOWN = 10;

    /**
     * The amount added to the action cooldown counter after digging out a tile of
     * dirt
     */
    public static final int DIG_COOLDOWN = 25; // TODO need to specify!

    /**
     * The amount added to the action cooldown counter after building a tile of dirt
     */
    public static final int BUILD_COOLDOWN = 35; // TODO need to specify!

    /**
     * The total time a rat can travel for while thrown (rats are stunned while
     * thrown)
     */
    public static final int THROW_DURATION = 40;

    /** The total time a rat is stunned after hitting the ground or a target */
    public static final int THROW_STUN_DURATION = 30;

}
