# Battlecode 2026 Python Documentation
v1.2.2

## Getting Started

**Instructions for Python competitors:**
- Clone the updated scaffold repository at github.com/battlecode/battlecode26-scaffold using the instructions on play.battlecode.org. (If you already have a scaffold clone, you'll need to make a new one.)
- Use Visual Studio Code (recommended) or another editor of your choice to open the scaffold directory.
- Open a terminal window (search for `terminal` on your computer, or `cmd` if you're on Windows), or press `Ctrl+Shift+P` and search for `Create New Terminal (With Profile)` if you're using VS Code.
- Make your bot in `src/<bot-name-here>/bot.py`, where `<bot-name-here>` is replaced with your team name. An example Python player `examplefuncsplayer_py` is included with the new scaffold.
- Use the `./gradlew run` command (or `gradlew run` on Windows) to run matches in your terminal. See play.battlecode.org for the parameters of this command.
- Python support in the client runner is coming soon!

## Allowed Libraries

The Battlecode match runner restricts which libraries you are allowed to use, because bytecode counting is very difficult with arbitrary libraries, making it hard to ensure fairness between Python and Java when arbitrary libraries are allowed. Most libraries are disallowed, but the following libraries are allowed:

- `battlecode26`
- `random`
- `math`
- `enum`
- libraries included in the folder containing your bot

## Battlecode function list

Below is a list of functions you can use to control your team's rats. Most are the same as Java but with camelCase changed to snake\_case, but one key change is that some of the `MapLocation` and `UnitType` methods were made into global functions, while others were kept as methods for those classes. See the Javadoc for detailed explanations of each of these functions. If there is a method supported by the Java engine which is missing here, please let us know in the Battlecode Discord server.

Important: Use `log` instead of `print` for printing debug messages for your bot!

```python
def log(*messages) -> None:
    pass

def bottom_left_distance_squared_to(loc1: MapLocation, loc2: MapLocation) -> int:
    pass

def direction_to(loc1: MapLocation, loc2: MapLocation) -> Direction:
    pass

def distance_squared_to(loc1: MapLocation, loc2: MapLocation) -> int:
    pass

def is_adjacent_to(loc1: MapLocation, loc2: MapLocation) -> bool:
    pass

def is_within_distance_squared(loc1: MapLocation, loc2: MapLocation, distance_squared: int, theta: float = 360, use_bottom_left: bool = False) -> bool:
    pass

def get_all_type_locations(unit_type: UnitType, center: MapLocation) -> list[MapLocation]:
    pass

# use the alias "rc" for conciseness in your bot.py file when using RobotController methods
# all methods shown below are static, as they are meant to be called on the global class "rc"
class RobotController:
    def get_round_num() -> int:
        pass

    def get_map_width() -> int:
        pass

    def get_map_height() -> int:
        pass

    def adjacent_location(dir: Direction) -> MapLocation:
        pass

    def attack(loc: MapLocation, cheese_amount: int = 0) -> None:
        pass

    def become_rat_king() -> None:
        pass

    def build_rat(loc: MapLocation) -> None:
        pass

    def can_attack(loc: MapLocation, cheese_amount: int = 0) -> bool:
        pass

    def can_become_rat_king() -> bool:
        pass

    def can_build_rat(loc: MapLocation) -> bool:
        pass

    def can_carry_rat(loc: MapLocation) -> bool:
        pass

    def can_drop_rat(dir: Direction) -> bool:
        pass

    def can_move(dir: Direction) -> bool:
        pass

    def can_move_forward() -> bool:
        pass

    def can_pick_up_cheese(loc: MapLocation) -> bool:
        pass

    def can_place_cat_trap(loc: MapLocation) -> bool:
        pass

    def can_place_dirt(loc: MapLocation) -> bool:
        pass

    def can_place_rat_trap(loc: MapLocation) -> bool:
        pass

    def can_remove_cat_trap(loc: MapLocation) -> bool:
        pass

    def can_remove_dirt(loc: MapLocation) -> bool:
        pass

    def can_remove_rat_trap(loc: MapLocation) -> bool:
        pass

    def can_sense_location(loc: MapLocation) -> bool:
        pass

    def can_sense_robot(id: int) -> bool:
        pass

    def can_sense_robot_at_location(loc: MapLocation) -> bool:
        pass

    def can_throw_rat() -> bool:
        pass

    def can_transfer_cheese(loc: MapLocation, cheese_amount: int) -> bool:
        pass

    def can_turn(dir: Direction = ...) -> bool:
        """
        Possible parameter combinations:
        - can_turn()
        - can_turn(dir=[value])
        """
        pass

    def carry_rat(loc: MapLocation) -> None:
        pass

    def disintegrate() -> None:
        pass

    def drop_rat(dir: Direction) -> None:
        pass

    def get_action_cooldown_turns() -> int:
        pass

    def get_all_cheese() -> int:
        pass

    def get_all_locations_within_radius_squared(center: MapLocation, radius_squared: int) -> list[MapLocation]:
        pass

    def get_all_part_locations() -> list[MapLocation]:
        pass

    def get_backstabbing_team() -> Team:
        pass

    def get_carrying() -> RobotInfo:
        pass

    def get_current_rat_cost() -> int:
        pass

    def get_direction() -> Direction:
        pass

    def get_dirt() -> int:
        pass

    def get_global_cheese() -> int:
        pass

    def get_health() -> int:
        pass

    def get_id() -> int:
        pass

    def get_location() -> MapLocation:
        pass

    def get_movement_cooldown_turns() -> int:
        pass

    def get_number_rat_traps() -> int:
        pass

    def get_number_cat_traps() -> int:
        pass

    def get_raw_cheese() -> int:
        pass

    def get_team() -> Team:
        pass

    def get_turning_cooldown_turns() -> int:
        pass

    def get_type() -> UnitType:
        pass

    def is_action_ready() -> bool:
        pass

    def is_being_carried() -> bool:
        pass

    def is_being_thrown() -> bool:
        pass

    def is_cooperation() -> bool:
        pass

    def is_location_occupied(loc: MapLocation) -> bool:
        pass

    def is_movement_ready() -> bool:
        pass

    def is_turning_ready() -> bool:
        pass

    def move(dir: Direction) -> None:
        pass

    def move_forward() -> None:
        pass

    def on_the_map(loc: MapLocation) -> bool:
        pass

    def pick_up_cheese(loc: MapLocation, amount: int = ...) -> None:
        """
        Calling this function with only the first argument makes the rat
        pick up the maximum amount of cheese possible.
        """
        pass

    def place_cat_trap(loc: MapLocation) -> None:
        pass

    def place_dirt(loc: MapLocation) -> None:
        pass

    def place_rat_trap(loc: MapLocation) -> None:
        pass

    def read_shared_array(index: int) -> int:
        pass

    def read_squeaks(roundNum: int) -> list[Message]:
        pass

    def remove_cat_trap(loc: MapLocation) -> None:
        pass

    def remove_dirt(loc: MapLocation) -> None:
        pass

    def remove_rat_trap(loc: MapLocation) -> None:
        pass

    def resign() -> None:
        pass

    def sense_map_info(loc: MapLocation) -> MapInfo:
        pass

    def sense_nearby_map_infos(center: MapLocation = ..., radius_squared: int = ...) -> list[MapInfo]:
        """
        Possible parameter combinations:
        - sense_nearby_map_infos()
        - sense_nearby_map_infos(center=[value])
        - sense_nearby_map_infos(radius_squared=[value])
        - sense_nearby_map_infos(center=[value], radius_squared=[value])
        """
        pass

    def sense_nearby_robots(center: MapLocation = ..., radius_squared: int = ..., team: Team = ...) -> list[RobotInfo]:
        """
        Possible parameter combinations:
        - sense_nearby_robots()
        - sense_nearby_robots(radius_squared=[value])
        - sense_nearby_robots(radius_squared=[value], team=[value])
        - sense_nearby_robots(center=[value], radius_squared=[value], team=[value])
        """
        pass

    def sense_passability(loc: MapLocation) -> int:
        pass

    def sense_robot(id: int) -> RobotInfo:
        pass

    def sense_robot_at_location(loc: MapLocation) -> RobotInfo:
        pass

    def set_indicator_dot(loc: MapLocation, r: int, g: int, b: int) -> None:
        pass

    def set_indicator_string(text: str) -> None:
        pass

    def set_indicator_line(startLoc: MapLocation, endLoc: MapLocation, r: int, g: int, b: int) -> None:
        pass

    def set_timeline_marker(text: str, r: int, g: int, b: int) -> None:
        pass

    def squeak(squeak: int) -> None:
        pass

    def throw_rat() -> None:
        pass

    def transfer_cheese(loc: MapLocation, cheese_amount: int) -> None:
        pass

    def turn(dir: Direction) -> None:
        pass

    def write_shared_array(index: int, value: int) -> None:
        pass


class GameActionExceptionType(Enum):
    INTERNAL_ERROR = 0
    NOT_ENOUGH_RESOURCE = 1
    CANT_MOVE_THERE = 2
    IS_NOT_READY = 3
    CANT_SENSE_THAT = 4
    OUT_OF_RANGE = 5
    CANT_DO_THAT = 6
    NO_ROBOT_THERE = 7
    ROUND_OUT_OF_RANGE = 8


class GameActionException(Exception):
    """
    Fields:
    - type: GameActionExceptionType
    - message: str (cannot use e.message, must use str(e) to get the message)
    """
    pass


class Team(Enum):
    A = 0
    B = 1
    NEUTRAL = 2  # for cats

    def opponent(self) -> Team:
        pass

    def ordinal(self) -> int:
        """
        Converts Teams into unique ints for use in the shared array and squeaks.
        """
        pass


class Direction(Enum):
    """
    Fields:
    - dx: int
    - dy: int
    """
    NORTH = (0, 1)
    NORTHEAST = (1, 1)
    EAST = (1, 0)
    SOUTHEAST = (1, -1)
    SOUTH = (0, -1)
    SOUTHWEST = (-1, -1)
    WEST = (-1, 0)
    NORTHWEST = (-1, 1)
    CENTER = (0, 0)

    def opposite(self) -> Direction:
        pass

    def rotate_left(self) -> Direction:  # by 45 degrees
        pass

    def rotate_right(self) -> Direction:  # by 45 degrees
        pass

    def all_directions() -> list[Direction]:
        pass

    def cardinal_directions() -> list[Direction]:
        pass

    def get_delta_x(self) -> int:
        pass

    def get_delta_y(self) -> int:
        pass

    def ordinal(self) -> int:
        """
        Converts Directions into unique ints for use in the shared array and squeaks.
        """
        pass


class MapLocation:
    """
    Fields:
    - x: int
    - y: int
    """

    def add(self, dir: Direction) -> MapLocation:
        pass

    def subtract(self, dir: Direction) -> MapLocation:
        pass

    def translate(self, dx: int, dy: int) -> MapLocation:
        pass


class UnitType(Enum):
    """
    Fields:
    - health: int
    - size: int
    - vision_cone_radius_squared: int
    - vision_cone_angle: int
    - action_cooldown: int
    - movement_cooldown: int
    - bytecode_limit: int
    """
    BABY_RAT = (100, 1, 20, 90, 10, 10, 17500)
    RAT_KING = (500, 3, 25, 360, 10, 40, 20000)
    CAT = (10_000, 2, 17, 180, 15, 20, 17500)

    def uses_bottom_left_location_for_distance(self) -> bool:
        pass

    def is_robot_type(self) -> bool:
        pass

    def is_throwable_type(self) -> bool:
        pass

    def is_throwing_type(self) -> bool:
        pass

    def is_baby_rat_type(self) -> bool:
        pass

    def is_rat_king_type(self) -> bool:
        pass

    def is_cat_type(self) -> bool:
        pass

    def ordinal(self) -> int:
        """
        Converts UnitTypes into unique ints for use in the shared array and squeaks.
        """
        pass


class TrapType(Enum):
    """
    Fields:
    - build_cost: int
    - damage: int
    - stun_time: int
    - action_cooldown: int
    - max_count: int
    - trigger_radius_squared: int
    """
    RAT_TRAP = (30, 50, 20, 15, 25, 2)
    CAT_TRAP = (10, 100, 20, 10, 10, 2)
    NONE = (0, 0, 0, 0, 0, 0)

    def ordinal(self) -> int:
        """
        Converts TrapTypes into unique ints for use in the shared array and squeaks.
        """
        pass


class MapInfo:
    """
    Fields:
    - location: MapLocation
    - is_passable: bool
    - flying_robot: RobotInfo
    - is_wall: bool
    - is_dirt: bool
    - cheese_amount: int
    - trap: TrapType
    - has_cheese_mine: bool
    """
    pass


class RobotInfo:
    """
    Fields:
    - id: int
    - team: Team
    - type: UnitType
    - health: int
    - location: MapLocation
    - direction: Direction
    - chirality: int
    - cheese_amount: int
    - carrying_robot: RobotInfo
    """
    pass


class Message:
    """
    Fields:
    - bytes: int
    - sender_id: int
    - round: int
    - source_loc: MapLocation
    """
    pass


class GameConstants:
    SPEC_VERSION = "1"
    MAP_MIN_HEIGHT = 20
    MAP_MAX_HEIGHT = 60
    MAP_MIN_WIDTH = 20
    MAP_MAX_WIDTH = 60
    MIN_CHEESE_MINE_SPACING_SQUARED = 25
    MAX_DIRT_PERCENTAGE = 50
    MAX_WALL_PERCENTAGE = 20
    GAME_DEFAULT_SEED = 6370
    GAME_MAX_NUMBER_OF_ROUNDS = 2000
    INDICATOR_STRING_MAX_LENGTH = 256
    TIMELINE_LABEL_MAX_LENGTH = 64
    EXCEPTION_BYTECODE_PENALTY = 500
    INITIAL_TEAM_CHEESE = 2500
    MAX_NUMBER_OF_RAT_KINGS = 5
    MAX_NUMBER_OF_RAT_KINGS_AFTER_CUTOFF = 2
    RAT_KING_CUTOFF_ROUND = 1200
    MAX_TEAM_EXECUTION_TIME = 1200000000000
    MOVE_STRAFE_COOLDOWN = 18
    CHEESE_COOLDOWN_PENALTY = 0.01
    RAT_KING_CHEESE_CONSUMPTION = 2
    RAT_KING_HEALTH_LOSS = 10
    CHEESE_MINE_SPAWN_PROBABILITY = 0.01
    CHEESE_SPAWN_RADIUS = 4
    CHEESE_SPAWN_AMOUNT = 20
    NUMBER_INITIAL_RAT_KINGS = 1
    CHEESE_TRANSFER_RADIUS_SQUARED = 9
    CHEESE_PICK_UP_RADIUS_SQUARED = 2
    BUILD_ROBOT_RADIUS_SQUARED = 4
    BUILD_ROBOT_BASE_COST = 10
    BUILD_ROBOT_COST_INCREASE = 10
    NUM_ROBOTS_FOR_COST_INCREASE = 4
    BUILD_DISTANCE_SQUARED = 2
    RAT_KING_BUILD_DISTANCE_SQUARED = 8
    ATTACK_DISTANCE_SQUARED = 2
    RAT_KING_ATTACK_DISTANCE_SQUARED = 8
    MESSAGE_ROUND_DURATION = 5
    MAX_MESSAGES_SENT_ROBOT = 1
    SQUEAK_RADIUS_SQUARED = 16
    THROW_DAMAGE = 20
    THROW_DAMAGE_PER_TILE = 5
    RAT_BITE_DAMAGE = 10
    CAT_SCRATCH_DAMAGE = 50
    CAT_POUNCE_MAX_DISTANCE_SQUARED = 9
    CAT_DIG_ADDITIONAL_COOLDOWN = 5
    HEALTH_GRAB_THRESHOLD = 0
    RAT_KING_UPGRADE_CHEESE_COST = 50
    DIG_DIRT_CHEESE_COST = 5
    PLACE_DIRT_CHEESE_COST = 3
    SHARED_ARRAY_SIZE = 64
    COMM_ARRAY_MAX_VALUE = 1023
    COOLDOWN_LIMIT = 10
    COOLDOWNS_PER_TURN = 10
    TURNING_COOLDOWN = 10
    BUILD_ROBOT_COOLDOWN = 10
    CHEESE_TRANSFER_COOLDOWN = 10
    DIG_COOLDOWN = 25
    CARRY_COOLDOWN_MULTIPLIER = 1.5
    MAX_CARRY_TOWER_HEIGHT = 2
    MAX_CARRY_DURATION = 10
    SAME_ROBOT_CARRY_COOLDOWN_TURNS = 2
    THROW_DURATION = 4
    HIT_GROUND_COOLDOWN = 10
    HIT_TARGET_COOLDOWN = 30
    CAT_SLEEP_TIME = 2
```
