from enum import Enum as _Enum


class GameActionExceptionType(_Enum):
    INTERNAL_ERROR = 0
    NOT_ENOUGH_RESOURCE = 1
    CANT_MOVE_THERE = 2
    IS_NOT_READY = 3
    CANT_SENSE_THAT = 4
    OUT_OF_RANGE = 5
    CANT_DO_THAT = 6
    NO_ROBOT_THERE = 7
    ROUND_OUT_OF_RANGE = 8

    def __str__(self):
        return self.name
    
    def __repr__(self):
        return f"GameActionExceptionType.{self.name}"


class GameActionException(Exception):
    def __init__(self, exc_type: GameActionExceptionType, message: str):
        super().__init__(message)
        self.type = exc_type
    
    def __str__(self):
        return f"GameActionException of type {self.type}: {super().__str__()}"

    def __repr__(self):
        return f"GameActionException(type={repr(self.type)}, message={repr(super().__str__())})"


class Team(_Enum):
    A = 0
    B = 1
    NEUTRAL = 2

    def opponent(self) -> 'Team':
        match self:
            case Team.A:
                return Team.B
            case Team.B:
                return Team.A
            case Team.NEUTRAL:
                return Team.NEUTRAL
    
    def ordinal(self) -> int:
        return self.value
    
    def __str__(self) -> str:
        return self.name
    
    def __repr__(self) -> str:
        return f"Team.{self.name}"


# make sure this matches the Java Direction enum, this could cause bugs
class Direction(_Enum):
    NORTH = (0, 1)
    NORTHEAST = (1, 1)
    EAST = (1, 0)
    SOUTHEAST = (1, -1)
    SOUTH = (0, -1)
    SOUTHWEST = (-1, -1)
    WEST = (-1, 0)
    NORTHWEST = (-1, 1)
    CENTER = (0, 0)

    def __init__(self, dx: int, dy: int):
        self.dx = dx
        self.dy = dy

    def opposite(self) -> 'Direction':
        if self == Direction.CENTER:
            return self
        return _dir_order[(_dir_to_index[self] + 4) % 8]

    def rotate_left(self) -> 'Direction':
        if self == Direction.CENTER:
            return self
        return _dir_order[(_dir_to_index[self] - 1) % 8]
    
    def rotate_right(self) -> 'Direction':
        if self == Direction.CENTER:
            return self
        return _dir_order[(_dir_to_index[self] + 1) % 8]
    
    def all_directions() -> list['Direction']:
        return list(Direction)

    def cardinal_directions() -> list['Direction']:
        return [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]
    
    def get_delta_x(self) -> int:
        return self.dx
    
    def get_delta_y(self) -> int:
        return self.dy
    
    def ordinal(self) -> int:
        return _dir_to_index[self]
    
    def __str__(self) -> str:
        return self.name
    
    def __repr__(self) -> str:
        return f"Direction.{self.name}"


_dir_order = list(Direction)
_dir_to_index = {dir: index for index, dir in enumerate(_dir_order)}


class MapLocation:
    def __init__(self, x: int, y: int):
        self.x = x
        self.y = y

    def add(self, dir: Direction) -> 'MapLocation':
        return MapLocation(self.x + dir.dx, self.y + dir.dy)
    
    def subtract(self, dir: Direction) -> 'MapLocation':
        return MapLocation(self.x - dir.dx, self.y - dir.dy)
    
    def translate(self, dx: int, dy: int) -> 'MapLocation':
        return MapLocation(self.x + dx, self.y + dy)
    
    def __eq__(self, other) -> bool:
        if not isinstance(other, MapLocation):
            return False
        return self.x == other.x and self.y == other.y
    
    def __str__(self) -> str:
        return f"[{self.x}, {self.y}]"
    
    def __repr__(self) -> str:
        return f"MapLocation({self.x}, {self.y})"


class UnitType(_Enum):
    BABY_RAT = (100, 1, 20, 90, 10, 10, 17500)
    RAT_KING = (500, 3, 25, 360, 10, 40, 20000)
    CAT = (10_000, 2, 17, 180, 15, 20, 17500)

    def __init__(self, health: int, size: int, vision_cone_radius_squared: int, vision_cone_angle: int,
                 action_cooldown: int, movement_cooldown: int, bytecode_limit: int):
        self.health = health
        self.size = size
        self.vision_cone_radius_squared = vision_cone_radius_squared
        self.vision_cone_angle = vision_cone_angle
        self.action_cooldown = action_cooldown
        self.movement_cooldown = movement_cooldown
        self.bytecode_limit = bytecode_limit
    
    def uses_bottom_left_location_for_distance(self) -> bool:
        return self == UnitType.CAT
    
    def is_robot_type(self) -> bool:
        return self in {UnitType.BABY_RAT, UnitType.RAT_KING, UnitType.CAT}
    
    def is_throwable_type(self) -> bool:
        return self == UnitType.BABY_RAT
    
    def is_throwing_type(self) -> bool:
        return self == UnitType.BABY_RAT
    
    def is_baby_rat_type(self) -> bool:
        return self == UnitType.BABY_RAT
    
    def is_rat_king_type(self) -> bool:
        return self == UnitType.RAT_KING
    
    def is_cat_type(self) -> bool:
        return self == UnitType.CAT
    
    def ordinal(self) -> int:
        return _ut_to_index[self]

    def __str__(self) -> str:
        return self.name
    
    def __repr__(self) -> str:
        return f"UnitType.{self.name}"


_ut_order = list(UnitType)
_ut_to_index = {ut: index for index, ut in enumerate(_ut_order)}


class TrapType(_Enum):
    RAT_TRAP = (30, 50, 20, 15, 25, 2)
    CAT_TRAP = (10, 100, 20, 10, 10, 2)
    NONE = (0, 0, 0, 0, 0, 0)

    def __init__(self, build_cost: int, damage: int, stun_time: int,
                 action_cooldown: int, max_count: int, trigger_radius_squared: int):
        self.build_cost = build_cost
        self.damage = damage
        self.stun_time = stun_time
        self.action_cooldown = action_cooldown
        self.max_count = max_count
        self.trigger_radius_squared = trigger_radius_squared
    
    def ordinal(self) -> int:
        return _trap_to_index[self]
    
    def __str__(self) -> str:
        return self.name
    
    def __repr__(self) -> str:
        return f"TrapType.{self.name}"


_trap_order = list(TrapType)
_trap_to_index = {trap: index for index, trap in enumerate(_trap_order)}


class RobotInfo:
    def __init__(self, id: int, team: Team, unit_type: UnitType, health: int,
                 location: MapLocation, direction: Direction,
                 chirality: int, cheese_amount: int, carrying_robot: 'RobotInfo'):
        self.id = id
        self.team = team
        self.type = unit_type
        self.health = health
        self.location = location
        self.direction = direction
        self.chirality = chirality
        self.cheese_amount = cheese_amount
        self.carrying_robot = carrying_robot


class MapInfo:
    def __init__(self, location: MapLocation, is_passable: bool, flying_robot: RobotInfo, is_wall: bool,
                 is_dirt: bool, cheese_amount: int, trap: TrapType, has_cheese_mine: bool):
        self.location = location
        self.is_passable = is_passable
        self.flying_robot = flying_robot
        self.is_wall = is_wall
        self.is_dirt = is_dirt
        self.cheese_amount = cheese_amount
        self.trap = trap
        self.has_cheese_mine = has_cheese_mine


class Message:
    def __init__(self, message_bytes: int, sender_id: int, round: int, source_loc: MapLocation):
        self.bytes = message_bytes
        self.sender_id = sender_id
        self.round = round
        self.source_loc = source_loc

    def __str__(self) -> str:
        return f"Message with value {self.bytes} sent from robot with ID {self.sender_id} during round {self.round} from location {self.source_loc}."

    def __repr__(self) -> str:
        return f"Message({self.bytes}, sender_id={self.sender_id}, round={self.round}, source_loc={self.source_loc})"


class GameConstants:
    """
    GameConstants defines constants that affect gameplay.
    Modifying these constants on the Python side does not affect the game at all,
    so there is no reason to modify them.
    """

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
