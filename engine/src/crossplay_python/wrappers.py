import sys as _sys
_sys.path.append("engine/src")

from crossplay_python.crossplay import CrossPlayMessage as _mess, CrossPlayLiteral as _lit, \
    CrossPlayMethod as _m, CrossPlayObjectType as _ot, CrossPlayReference as _ref, wait as _wait
from crossplay_python.classes import *

class RobotController:
    @staticmethod
    def _convert_param(p):
        # pass through CrossPlay objects
        try:
            if isinstance(p, _lit) or isinstance(p, _ref):
                return p
        except Exception:
            pass

        # primitives
        if isinstance(p, bool):
            return _lit(_ot.BOOLEAN, p)
        if isinstance(p, int):
            return _lit(_ot.INTEGER, p)
        if isinstance(p, float):
            return _lit(_ot.DOUBLE, p)
        if isinstance(p, str):
            return _lit(_ot.STRING, p)
        if isinstance(p, MapLocation):
            return _lit(_ot.MAP_LOCATION, p.to_tuple())
        if isinstance(p, tuple) or isinstance(p, list):
            # encode lists/tuples as ARRAY of literals (do NOT treat 2-tuples as map locations)
            return _lit(_ot.ARRAY, [_lit(_ot.INTEGER, int(x)) if isinstance(x, int) else _lit(_ot.STRING, str(x)) for x in p])
        # enums from crossplay_python.enums
        try:
            from crossplay_python.classes import Team as _Team
            if isinstance(p, _Team):
                return _lit(_ot.TEAM, p)
        except Exception:
            pass

        # fallback: pass as-is (caller can provide _lit/_ref)
        return p

    @staticmethod
    def _call(method, *args):
        params = [_ref(_ot.ROBOT_CONTROLLER, 0)]
        for a in args:
            params.append(RobotController._convert_param(a))
        return _wait(_mess(method, params))

    # Basic getters
    def get_round_num():
        return RobotController._call(_m.RC_GET_ROUND_NUM)

    def get_map_width():
        return RobotController._call(_m.RC_GET_MAP_WIDTH)

    def get_map_height():
        return RobotController._call(_m.RC_GET_MAP_HEIGHT)

    def is_cooperation():
        return RobotController._call(_m.RC_IS_COOPERATION)

    def get_id():
        return RobotController._call(_m.RC_GET_ID)

    def get_team():
        return RobotController._call(_m.RC_GET_TEAM)

    def get_location():
        return RobotController._call(_m.RC_GET_LOCATION)

    def get_all_part_locations():
        return RobotController._call(_m.RC_GET_ALL_PART_LOCATIONS)

    def get_direction():
        return RobotController._call(_m.RC_GET_DIRECTION)

    def get_health():
        return RobotController._call(_m.RC_GET_HEALTH)

    def get_raw_cheese():
        return RobotController._call(_m.RC_GET_RAW_CHEESE)

    def get_global_cheese():
        return RobotController._call(_m.RC_GET_GLOBAL_CHEESE)

    def get_all_cheese():
        return RobotController._call(_m.RC_GET_ALL_CHEESE)

    def get_dirt():
        return RobotController._call(_m.RC_GET_DIRT)

    def get_type():
        return RobotController._call(_m.RC_GET_TYPE)

    def get_carrying():
        return RobotController._call(_m.RC_GET_CARRYING)

    def is_being_thrown():
        return RobotController._call(_m.RC_IS_BEING_THROWN)

    def is_being_carried():
        return RobotController._call(_m.RC_IS_BEING_CARRIED)

    def on_the_map(loc):
        return RobotController._call(_m.RC_ON_THE_MAP, loc)

    def can_sense_location(loc):
        return RobotController._call(_m.RC_CAN_SENSE_LOCATION, loc)

    def is_location_occupied(loc):
        return RobotController._call(_m.RC_IS_LOCATION_OCCUPIED, loc)

    def can_sense_robot_at_location(loc):
        return RobotController._call(_m.RC_CAN_SENSE_ROBOT_AT_LOCATION, loc)

    def sense_robot_at_location(loc):
        return RobotController._call(_m.RC_SENSE_ROBOT_AT_LOCATION, loc)

    def can_sense_robot(robot_ref):
        return RobotController._call(_m.RC_CAN_SENSE_ROBOT, robot_ref)

    def sense_robot(robot_ref):
        return RobotController._call(_m.RC_SENSE_ROBOT, robot_ref)

    def sense_nearby_robots(*args):
        return RobotController._call(_m.RC_SENSE_NEARBY_ROBOTS, *args)

    def sense_nearby_robots_int(radius):
        return RobotController._call(_m.RC_SENSE_NEARBY_ROBOTS__INT, radius)

    def sense_nearby_robots_int_team(radius, team):
        return RobotController._call(_m.RC_SENSE_NEARBY_ROBOTS__INT_TEAM, radius, team)

    def sense_nearby_robots_loc_int_team(loc, radius, team):
        return RobotController._call(_m.RC_SENSE_NEARBY_ROBOTS__LOC_INT_TEAM, loc, radius, team)

    def sense_passability(loc):
        return RobotController._call(_m.RC_SENSE_PASSABILITY, loc)

    def sense_map_info(loc):
        return RobotController._call(_m.RC_SENSE_MAP_INFO, loc)

    def sense_nearby_map_infos():
        return RobotController._call(_m.RC_SENSE_NEARBY_MAP_INFOS)

    def sense_nearby_map_infos_int(radius):
        return RobotController._call(_m.RC_SENSE_NEARBY_MAP_INFOS__INT, radius)

    def sense_nearby_map_infos_loc(loc):
        return RobotController._call(_m.RC_SENSE_NEARBY_MAP_INFOS__LOC, loc)

    def sense_nearby_map_infos_loc_int(loc, radius):
        return RobotController._call(_m.RC_SENSE_NEARBY_MAP_INFOS__LOC_INT, loc, radius)

    def adjacent_location(loc, direction):
        return RobotController._call(_m.RC_ADJACENT_LOCATION, loc, direction)

    def get_all_locations_within_radius_squared(loc, r2):
        return RobotController._call(_m.RC_GET_ALL_LOCATIONS_WITHIN_RADIUS_SQUARED, loc, r2)

    def is_action_ready():
        return RobotController._call(_m.RC_IS_ACTION_READY)

    def get_action_cooldown_turns():
        return RobotController._call(_m.RC_GET_ACTION_COOLDOWN_TURNS)

    def is_movement_ready():
        return RobotController._call(_m.RC_IS_MOVEMENT_READY)

    def is_turning_ready():
        return RobotController._call(_m.RC_IS_TURNING_READY)

    def get_movement_cooldown_turns():
        return RobotController._call(_m.RC_GET_MOVEMENT_COOLDOWN_TURNS)

    def get_turning_cooldown_turns():
        return RobotController._call(_m.RC_GET_TURNING_COOLDOWN_TURNS)

    def can_move_forward():
        return RobotController._call(_m.RC_CAN_MOVE_FORWARD)

    def can_move():
        return RobotController._call(_m.RC_CAN_MOVE)

    def move_forward():
        return RobotController._call(_m.RC_MOVE_FORWARD)

    def move(direction):
        return RobotController._call(_m.RC_MOVE, direction)

    def can_turn():
        return RobotController._call(_m.RC_CAN_TURN)

    def turn(direction):
        return RobotController._call(_m.RC_TURN, direction)

    def get_current_rat_cost():
        return RobotController._call(_m.RC_GET_CURRENT_RAT_COST)

    def can_build_robot(type_int):
        return RobotController._call(_m.RC_CAN_BUILD_ROBOT, type_int)

    def build_robot(type_int):
        return RobotController._call(_m.RC_BUILD_ROBOT, type_int)

    def can_become_rat_king():
        return RobotController._call(_m.RC_CAN_BECOME_RAT_KING)

    def become_rat_king():
        return RobotController._call(_m.RC_BECOME_RAT_KING)

    def can_place_dirt():
        return RobotController._call(_m.RC_CAN_PLACE_DIRT)

    def place_dirt():
        return RobotController._call(_m.RC_PLACE_DIRT)

    def can_remove_dirt():
        return RobotController._call(_m.RC_CAN_REMOVE_DIRT)

    def remove_dirt():
        return RobotController._call(_m.RC_REMOVE_DIRT)

    def can_place_rat_trap():
        return RobotController._call(_m.RC_CAN_PLACE_RAT_TRAP)

    def place_rat_trap():
        return RobotController._call(_m.RC_PLACE_RAT_TRAP)

    def can_remove_rat_trap():
        return RobotController._call(_m.RC_CAN_REMOVE_RAT_TRAP)

    def remove_rat_trap():
        return RobotController._call(_m.RC_REMOVE_RAT_TRAP)

    def can_place_cat_trap():
        return RobotController._call(_m.RC_CAN_PLACE_CAT_TRAP)

    def place_cat_trap():
        return RobotController._call(_m.RC_PLACE_CAT_TRAP)

    def can_remove_cat_trap():
        return RobotController._call(_m.RC_CAN_REMOVE_CAT_TRAP)

    def remove_cat_trap():
        return RobotController._call(_m.RC_REMOVE_CAT_TRAP)

    def can_pick_up_cheese():
        return RobotController._call(_m.RC_CAN_PICK_UP_CHEESE)

    def pick_up_cheese():
        return RobotController._call(_m.RC_PICK_UP_CHEESE)

    def can_attack():
        return RobotController._call(_m.RC_CAN_ATTACK)

    def can_attack_loc_int(loc, i):
        return RobotController._call(_m.RC_CAN_ATTACK__LOC_INT, loc, i)

    def attack():
        return RobotController._call(_m.RC_ATTACK)

    def attack_loc_int(loc, i):
        return RobotController._call(_m.RC_ATTACK__LOC_INT, loc, i)

    def squeak():
        return RobotController._call(_m.RC_SQUEAK)

    def read_squeaks():
        return RobotController._call(_m.RC_READ_SQUEAKS)

    def write_shared_array(index, value):
        return RobotController._call(_m.RC_WRITE_SHARED_ARRAY, index, value)

    def read_shared_array(index):
        return RobotController._call(_m.RC_READ_SHARED_ARRAY, index)

    def write_persistent_array(index, value):
        return RobotController._call(_m.RC_WRITE_PERSISTENT_ARRAY, index, value)

    def read_persistent_array(index):
        return RobotController._call(_m.RC_READ_PERSISTENT_ARRAY, index)

    def can_transfer_cheese():
        return RobotController._call(_m.RC_CAN_TRANSFER_CHEESE)

    def transfer_cheese(amount):
        return RobotController._call(_m.RC_TRANSFER_CHEESE, amount)

    def throw_rat(target_loc, power):
        return RobotController._call(_m.RC_THROW_RAT, target_loc, power)

    def can_throw_rat():
        return RobotController._call(_m.RC_CAN_THROW_RAT)

    def drop_rat():
        return RobotController._call(_m.RC_DROP_RAT)

    def can_drop_rat():
        return RobotController._call(_m.RC_CAN_DROP_RAT)

    def can_carry_rat():
        return RobotController._call(_m.RC_CAN_CARRY_RAT)

    def carry_rat(robot_ref):
        return RobotController._call(_m.RC_CARRY_RAT, robot_ref)

    def disintegrate():
        return RobotController._call(_m.RC_DISINTEGRATE)

    def resign():
        return RobotController._call(_m.RC_RESIGN)

    def set_indicator_string(s):
        return RobotController._call(_m.RC_SET_INDICATOR_STRING, s)

    def set_indicator_dot(x, y):
        return RobotController._call(_m.RC_SET_INDICATOR_DOT, MapLocation(x, y))

    def set_indicator_line(x1, y1, x2, y2):
        return RobotController._call(_m.RC_SET_INDICATOR_LINE, MapLocation(x1, y1), MapLocation(x2, y2))

    def set_timeline_marker(marker):
        return RobotController._call(_m.RC_SET_TIMELINE_MARKER, marker)

rc = RobotController

def log(message):
    return _wait(_mess(_m.LOG, [_lit(_ot.STRING, message)]))

_GAME_METHODS = {'Direction': Direction,
                'Team': Team,
                'MapLocation': MapLocation,
                'GameActionException': GameActionException,
                'RobotController': RobotController,
                'log': (log, 1)}
