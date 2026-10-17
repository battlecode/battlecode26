from .crossplay import (
    CrossPlayMethod as _m,
    send_wait_and_parse as _wait,
)
from .classes import *
from typing import overload as _overload

class RobotController:
    @staticmethod
    def get_round_num() -> int:
        return _wait(_m.RC_GET_ROUND_NUM)

    @staticmethod
    def get_map_width() -> int:
        return _wait(_m.RC_GET_MAP_WIDTH)

    @staticmethod
    def get_map_height() -> int:
        return _wait(_m.RC_GET_MAP_HEIGHT)
    
    @staticmethod
    def adjacent_location(dir: Direction) -> MapLocation:
        return _wait(_m.RC_ADJACENT_LOCATION, [dir])
    
    @staticmethod
    def attack(loc: MapLocation, cheese_amount: int = 0) -> None:
        _wait(_m.RC_ATTACK__LOC_INT, [loc, cheese_amount])
    
    @staticmethod
    def become_rat_king() -> None:
        _wait(_m.RC_BECOME_RAT_KING)
    
    @staticmethod
    def build_rat(loc: MapLocation) -> None:
        _wait(_m.RC_BUILD_RAT, [loc])
    
    @staticmethod
    def can_attack(loc: MapLocation, cheese_amount: int = 0) -> bool:
        return _wait(_m.RC_CAN_ATTACK__LOC_INT, [loc, cheese_amount])
    
    @staticmethod
    def can_become_rat_king() -> bool:
        return _wait(_m.RC_CAN_BECOME_RAT_KING)
    
    @staticmethod
    def can_build_rat(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_BUILD_RAT, [loc])
    
    @staticmethod
    def can_carry_rat(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_CARRY_RAT, [loc])
    
    @staticmethod
    def can_drop_rat(dir: Direction) -> bool:
        return _wait(_m.RC_CAN_DROP_RAT, [dir])
    
    @staticmethod
    def can_move(dir: Direction) -> bool:
        return _wait(_m.RC_CAN_MOVE, [dir])
    
    @staticmethod
    def can_move_forward() -> bool:
        return _wait(_m.RC_CAN_MOVE_FORWARD)
    
    @staticmethod
    def can_pick_up_cheese(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_PICK_UP_CHEESE, [loc])
    
    @staticmethod
    def can_place_cat_trap(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_PLACE_CAT_TRAP, [loc])

    @staticmethod
    def can_place_dirt(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_PLACE_DIRT, [loc])

    @staticmethod
    def can_place_rat_trap(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_PLACE_RAT_TRAP, [loc])
    
    @staticmethod
    def can_remove_cat_trap(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_REMOVE_CAT_TRAP, [loc])
    
    @staticmethod
    def can_remove_dirt(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_REMOVE_DIRT, [loc])
    
    @staticmethod
    def can_remove_rat_trap(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_REMOVE_RAT_TRAP, [loc])
    
    @staticmethod
    def can_sense_location(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_SENSE_LOCATION, [loc])
    
    @staticmethod
    def can_sense_robot(id: int) -> bool:
        return _wait(_m.RC_CAN_SENSE_ROBOT, [id])
    
    @staticmethod
    def can_sense_robot_at_location(loc: MapLocation) -> bool:
        return _wait(_m.RC_CAN_SENSE_ROBOT_AT_LOCATION, [loc])
    
    @staticmethod
    def can_throw_rat() -> bool:
        return _wait(_m.RC_CAN_THROW_RAT)
    
    @staticmethod
    def can_transfer_cheese(loc: MapLocation, cheese_amount: int) -> bool:
        return _wait(_m.RC_CAN_TRANSFER_CHEESE, [loc, cheese_amount])
    
    @staticmethod
    def can_turn(dir: Direction = ...) -> bool:
        """
        Possible parameter combinations:
        - can_turn()
        - can_turn(dir=[value])
        """
        if dir is ...:
            return _wait(_m.RC_CAN_TURN, [])
        else:
            return _wait(_m.RC_CAN_TURN__DIR, [dir])

    @staticmethod
    def carry_rat(loc: MapLocation) -> None:
        _wait(_m.RC_CARRY_RAT, [loc])
    
    @staticmethod
    def disintegrate() -> None:
        _wait(_m.RC_DISINTEGRATE)

    @staticmethod
    def drop_rat(dir: Direction) -> None:
        _wait(_m.RC_DROP_RAT, [dir])

    @staticmethod
    def get_action_cooldown_turns() -> int:
        return _wait(_m.RC_GET_ACTION_COOLDOWN_TURNS)
    
    @staticmethod
    def get_all_cheese() -> int:
        return _wait(_m.RC_GET_ALL_CHEESE)
    
    @staticmethod
    def get_all_locations_within_radius_squared(center: MapLocation, radius_squared: int) -> list[MapLocation]:
        return _wait(_m.RC_GET_ALL_LOCATIONS_WITHIN_RADIUS_SQUARED, [center, radius_squared])
    
    @staticmethod
    def get_all_part_locations() -> list[MapLocation]:
        return _wait(_m.RC_GET_ALL_PART_LOCATIONS)

    @staticmethod
    def get_backstabbing_team() -> Team:
        return _wait(_m.RC_GET_BACKSTABBING_TEAM)

    @staticmethod
    def get_carrying() -> RobotInfo:
        return _wait(_m.RC_GET_CARRYING)
    
    @staticmethod
    def get_current_rat_cost() -> int:
        return _wait(_m.RC_GET_CURRENT_RAT_COST)
    
    @staticmethod
    def get_direction() -> Direction:
        return _wait(_m.RC_GET_DIRECTION)
    
    @staticmethod
    def get_dirt() -> int:
        return _wait(_m.RC_GET_DIRT)
    
    @staticmethod
    def get_global_cheese() -> int:
        return _wait(_m.RC_GET_GLOBAL_CHEESE)
    
    @staticmethod
    def get_health() -> int:
        return _wait(_m.RC_GET_HEALTH)
    
    @staticmethod
    def get_id() -> int:
        return _wait(_m.RC_GET_ID)
    
    @staticmethod
    def get_location() -> MapLocation:
        return _wait(_m.RC_GET_LOCATION)
    
    @staticmethod
    def get_movement_cooldown_turns() -> int:
        return _wait(_m.RC_GET_MOVEMENT_COOLDOWN_TURNS)

    @staticmethod
    def get_number_rat_traps() -> int:
        return _wait(_m.RC_GET_NUMBER_RAT_TRAPS)

    @staticmethod
    def get_number_cat_traps() -> int:
        return _wait(_m.RC_GET_NUMBER_CAT_TRAPS)

    @staticmethod
    def get_raw_cheese() -> int:
        return _wait(_m.RC_GET_RAW_CHEESE)
    
    @staticmethod
    def get_team() -> Team:
        return _wait(_m.RC_GET_TEAM)
    
    @staticmethod
    def get_turning_cooldown_turns() -> int:
        return _wait(_m.RC_GET_TURNING_COOLDOWN_TURNS)
    
    @staticmethod
    def get_type() -> UnitType:
        return _wait(_m.RC_GET_TYPE)
    
    @staticmethod
    def is_action_ready() -> bool:
        return _wait(_m.RC_IS_ACTION_READY)
    
    @staticmethod
    def is_being_carried() -> bool:
        return _wait(_m.RC_IS_BEING_CARRIED)
    
    @staticmethod
    def is_being_thrown() -> bool:
        return _wait(_m.RC_IS_BEING_THROWN)
    
    @staticmethod
    def is_cooperation() -> bool:
        return _wait(_m.RC_IS_COOPERATION)
    
    @staticmethod
    def is_location_occupied(loc: MapLocation) -> bool:
        return _wait(_m.RC_IS_LOCATION_OCCUPIED, [loc])
    
    @staticmethod
    def is_movement_ready() -> bool:
        return _wait(_m.RC_IS_MOVEMENT_READY)
    
    @staticmethod
    def is_turning_ready() -> bool:
        return _wait(_m.RC_IS_TURNING_READY)
    
    @staticmethod
    def move(dir: Direction) -> None:
        _wait(_m.RC_MOVE, [dir])

    @staticmethod
    def move_forward() -> None:
        _wait(_m.RC_MOVE_FORWARD)

    @staticmethod
    def on_the_map(loc: MapLocation) -> bool:
        return _wait(_m.RC_ON_THE_MAP, [loc])
    
    @staticmethod
    def pick_up_cheese(loc: MapLocation, amount: int = ...) -> None:
        if amount is ...:
            _wait(_m.RC_PICK_UP_CHEESE, [loc])
        else:
            _wait(_m.RC_PICK_UP_CHEESE__LOC_INT, [loc, amount])

    @staticmethod
    def place_cat_trap(loc: MapLocation) -> None:
        _wait(_m.RC_PLACE_CAT_TRAP, [loc])

    @staticmethod
    def place_dirt(loc: MapLocation) -> None:
        _wait(_m.RC_PLACE_DIRT, [loc])
    
    @staticmethod
    def place_rat_trap(loc: MapLocation) -> None:
        _wait(_m.RC_PLACE_RAT_TRAP, [loc])

    @staticmethod
    def read_shared_array(index: int) -> int:
        return _wait(_m.RC_READ_SHARED_ARRAY, [index])
    
    @staticmethod
    def read_squeaks(roundNum: int) -> list[Message]:
        return _wait(_m.RC_READ_SQUEAKS, [roundNum])
    
    @staticmethod
    def remove_cat_trap(loc: MapLocation) -> None:
        _wait(_m.RC_REMOVE_CAT_TRAP, [loc])

    @staticmethod
    def remove_dirt(loc: MapLocation) -> None:
        _wait(_m.RC_REMOVE_DIRT, [loc])

    @staticmethod
    def remove_rat_trap(loc: MapLocation) -> None:
        _wait(_m.RC_REMOVE_RAT_TRAP, [loc])

    @staticmethod
    def resign() -> None:
        _wait(_m.RC_RESIGN)

    @staticmethod
    def sense_map_info(loc: MapLocation) -> MapInfo:
        return _wait(_m.RC_SENSE_MAP_INFO, [loc])
    
    @staticmethod
    def sense_nearby_map_infos(center: MapLocation = ..., radius_squared: int = ...) -> list[MapInfo]:
        """
        Possible parameter combinations:
        - sense_nearby_map_infos()
        - sense_nearby_map_infos(center=[value])
        - sense_nearby_map_infos(radius_squared=[value])
        - sense_nearby_map_infos(center=[value], radius_squared=[value])
        """

        if center is ... and radius_squared is ...:
            return _wait(_m.RC_SENSE_NEARBY_MAP_INFOS, [])
        elif radius_squared is ...:
            return _wait(_m.RC_SENSE_NEARBY_MAP_INFOS__LOC, [center])
        elif center is ...:
            return _wait(_m.RC_SENSE_NEARBY_MAP_INFOS__INT, [radius_squared])
        else:
            return _wait(_m.RC_SENSE_NEARBY_MAP_INFOS__LOC_INT, [center, radius_squared])
    
    @staticmethod
    def sense_nearby_robots(center: MapLocation = ..., radius_squared: int = ..., team: Team = ...) -> list[RobotInfo]:
        """
        Possible parameter combinations:
        - sense_nearby_robots()
        - sense_nearby_robots(radius_squared=[value])
        - sense_nearby_robots(radius_squared=[value], team=[value])
        - sense_nearby_robots(center=[value], radius_squared=[value], team=[value])
        """

        if center is ... and radius_squared is ... and team is ...:
            return _wait(_m.RC_SENSE_NEARBY_ROBOTS, [])
        elif center is ... and radius_squared is not ... and team is ...:
            return _wait(_m.RC_SENSE_NEARBY_ROBOTS__INT, [radius_squared])
        elif center is ... and radius_squared is not ... and team is not ...:
            return _wait(_m.RC_SENSE_NEARBY_ROBOTS__INT_TEAM, [radius_squared, team])
        elif center is not ... and radius_squared is not ... and team is not ...:
            return _wait(_m.RC_SENSE_NEARBY_ROBOTS__LOC_INT_TEAM, [center, radius_squared, team])
        else:
            raise ValueError("Invalid combination of parameters for sense_nearby_robots. See docstring for valid combinations.")
    
    @staticmethod
    def sense_passability(loc: MapLocation) -> int:
        return _wait(_m.RC_SENSE_PASSABILITY, [loc])
    
    @staticmethod
    def sense_robot(id: int) -> RobotInfo:
        return _wait(_m.RC_SENSE_ROBOT, [id])

    @staticmethod
    def sense_robot_at_location(loc: MapLocation) -> RobotInfo:
        return _wait(_m.RC_SENSE_ROBOT_AT_LOCATION, [loc])
    
    @staticmethod
    def set_indicator_dot(loc: MapLocation, r: int, g: int, b: int) -> None:
        _wait(_m.RC_SET_INDICATOR_DOT, [loc, r, g, b])

    @staticmethod
    def set_indicator_string(text: str) -> None:
        _wait(_m.RC_SET_INDICATOR_STRING, [text])

    @staticmethod
    def set_indicator_line(startLoc: MapLocation, endLoc: MapLocation, r: int, g: int, b: int) -> None:
        _wait(_m.RC_SET_INDICATOR_LINE, [startLoc, endLoc, r, g, b])

    @staticmethod
    def set_timeline_marker(text: str, r: int, g: int, b: int) -> None:
        _wait(_m.RC_SET_TIMELINE_MARKER, [text, r, g, b])
    
    @staticmethod
    def squeak(squeak: int) -> None:
        _wait(_m.RC_SQUEAK, [squeak])
    
    @staticmethod
    def throw_rat() -> None:
        _wait(_m.RC_THROW_RAT)

    @staticmethod
    def transfer_cheese(loc: MapLocation, cheese_amount: int) -> None:
        _wait(_m.RC_TRANSFER_CHEESE, [loc, cheese_amount])
    
    @staticmethod
    def turn(dir: Direction) -> None:
        _wait(_m.RC_TURN, [dir])
    
    @staticmethod
    def write_shared_array(index: int, value: int) -> None:
        _wait(_m.RC_WRITE_SHARED_ARRAY, [index, value])


rc = RobotController


def log(*messages) -> None:
    return _wait(_m.LOG, [" ".join(map(str, messages))])


def bottom_left_distance_squared_to(loc1: MapLocation, loc2: MapLocation) -> int:
    return _wait(_m.ML_BOTTOM_LEFT_DISTANCE_SQUARED_TO, [loc1, loc2])


def direction_to(loc1: MapLocation, loc2: MapLocation) -> Direction:
    return _wait(_m.ML_DIRECTION_TO, [loc1, loc2])


def distance_squared_to(loc1: MapLocation, loc2: MapLocation) -> int:
    return _wait(_m.ML_DISTANCE_SQUARED_TO, [loc1, loc2])


def is_adjacent_to(loc1: MapLocation, loc2: MapLocation) -> bool:
    return _wait(_m.ML_IS_ADJACENT_TO, [loc1, loc2])


def is_within_distance_squared(loc1: MapLocation, loc2: MapLocation, distance_squared: int, theta: float = 360, use_bottom_left: bool = False) -> bool:
    return _wait(_m.ML_IS_WITHIN_DISTANCE_SQUARED__LOC_INT_DIR_DOUBLE_BOOLEAN, [loc1, loc2, distance_squared, theta, use_bottom_left])


def get_all_type_locations(unit_type: UnitType, center: MapLocation) -> list[MapLocation]:
    return _wait(_m.UT_GET_ALL_TYPE_LOCATIONS, [unit_type, center])


_GAME_METHODS = {
    "GameActionException": GameActionException,
    "Team": Team,
    "Direction": Direction,
    "MapLocation": MapLocation,
    "UnitType": UnitType,
    "TrapType": TrapType,
    "RobotInfo": RobotInfo,
    "MapInfo": MapInfo,
    "rc": rc,
    "log": log,
    "bottom_left_distance_squared_to": bottom_left_distance_squared_to,
    "direction_to": direction_to,
    "distance_squared_to": distance_squared_to,
    "is_adjacent_to": is_adjacent_to,
    "is_within_distance_squared": is_within_distance_squared,
    "get_all_type_locations": get_all_type_locations,
}
