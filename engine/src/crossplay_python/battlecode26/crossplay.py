import socket
import struct
import json
import time
from enum import Enum
from collections import deque
from .classes import *

# Connection constants
IPC_HOST = "127.0.0.1"
IPC_PORT = 27185
BYTECODE_LIMIT = 20000  # Default, updated by game


class CrossPlayException(Exception):
    def __init__(self, message):
        super().__init__(
            message
            + " (If you are a competitor, please report this to the Battlecode staff.)"
        )


class CrossPlayMethod(Enum):
    INVALID = 0
    END_TURN = 1
    RC_GET_ROUND_NUM = 2
    RC_GET_MAP_WIDTH = 3
    RC_GET_MAP_HEIGHT = 4
    RC_IS_COOPERATION = 5
    RC_GET_ID = 6
    RC_GET_TEAM = 7
    RC_GET_LOCATION = 8
    RC_GET_ALL_PART_LOCATIONS = 9
    RC_GET_DIRECTION = 10
    RC_GET_HEALTH = 11
    RC_GET_RAW_CHEESE = 12
    RC_GET_GLOBAL_CHEESE = 13
    RC_GET_ALL_CHEESE = 14
    RC_GET_DIRT = 15
    RC_GET_TYPE = 16
    RC_GET_CARRYING = 17
    RC_IS_BEING_THROWN = 18
    RC_IS_BEING_CARRIED = 19
    RC_ON_THE_MAP = 20
    RC_CAN_SENSE_LOCATION = 21
    RC_IS_LOCATION_OCCUPIED = 22
    RC_CAN_SENSE_ROBOT_AT_LOCATION = 23
    RC_SENSE_ROBOT_AT_LOCATION = 24
    RC_CAN_SENSE_ROBOT = 25
    RC_SENSE_ROBOT = 26
    RC_SENSE_NEARBY_ROBOTS = 27
    RC_SENSE_NEARBY_ROBOTS__INT = 28
    RC_SENSE_NEARBY_ROBOTS__INT_TEAM = 29
    RC_SENSE_NEARBY_ROBOTS__LOC_INT_TEAM = 30
    RC_SENSE_PASSABILITY = 31
    RC_SENSE_MAP_INFO = 32
    RC_SENSE_NEARBY_MAP_INFOS = 33
    RC_SENSE_NEARBY_MAP_INFOS__INT = 34
    RC_SENSE_NEARBY_MAP_INFOS__LOC = 35
    RC_SENSE_NEARBY_MAP_INFOS__LOC_INT = 36
    RC_ADJACENT_LOCATION = 37
    RC_GET_ALL_LOCATIONS_WITHIN_RADIUS_SQUARED = 38
    RC_IS_ACTION_READY = 39
    RC_GET_ACTION_COOLDOWN_TURNS = 40
    RC_IS_MOVEMENT_READY = 41
    RC_IS_TURNING_READY = 42
    RC_GET_MOVEMENT_COOLDOWN_TURNS = 43
    RC_GET_TURNING_COOLDOWN_TURNS = 44
    RC_CAN_MOVE_FORWARD = 45
    RC_CAN_MOVE = 46
    RC_MOVE_FORWARD = 47
    RC_MOVE = 48
    RC_CAN_TURN = 49
    RC_TURN = 50
    RC_GET_CURRENT_RAT_COST = 51
    RC_CAN_BUILD_RAT = 52
    RC_BUILD_RAT = 53
    RC_CAN_BECOME_RAT_KING = 54
    RC_BECOME_RAT_KING = 55
    RC_CAN_PLACE_DIRT = 56
    RC_PLACE_DIRT = 57
    RC_CAN_REMOVE_DIRT = 58
    RC_REMOVE_DIRT = 59
    RC_CAN_PLACE_RAT_TRAP = 60
    RC_PLACE_RAT_TRAP = 61
    RC_CAN_REMOVE_RAT_TRAP = 62
    RC_REMOVE_RAT_TRAP = 63
    RC_CAN_PLACE_CAT_TRAP = 64
    RC_PLACE_CAT_TRAP = 65
    RC_CAN_REMOVE_CAT_TRAP = 66
    RC_REMOVE_CAT_TRAP = 67
    RC_CAN_PICK_UP_CHEESE = 68
    RC_PICK_UP_CHEESE = 69
    RC_PICK_UP_CHEESE__LOC_INT = 70
    RC_CAN_ATTACK = 71
    RC_CAN_ATTACK__LOC_INT = 72
    RC_ATTACK = 73
    RC_ATTACK__LOC_INT = 74
    RC_SQUEAK = 75
    RC_READ_SQUEAKS = 76
    RC_WRITE_SHARED_ARRAY = 77
    RC_READ_SHARED_ARRAY = 78
    RC_CAN_TRANSFER_CHEESE = 79
    RC_TRANSFER_CHEESE = 80
    RC_THROW_RAT = 81
    RC_CAN_THROW_RAT = 82
    RC_DROP_RAT = 83
    RC_CAN_DROP_RAT = 84
    RC_CAN_CARRY_RAT = 85
    RC_CARRY_RAT = 86
    RC_DISINTEGRATE = 87
    RC_RESIGN = 88
    RC_SET_INDICATOR_STRING = 89
    RC_SET_INDICATOR_DOT = 90
    RC_SET_INDICATOR_LINE = 91
    RC_SET_TIMELINE_MARKER = 92
    RC_CAN_TURN__DIR = 93
    ML_DISTANCE_SQUARED_TO = 94
    ML_BOTTOM_LEFT_DISTANCE_SQUARED_TO = 95
    ML_IS_WITHIN_DISTANCE_SQUARED = 96
    ML_IS_WITHIN_DISTANCE_SQUARED__LOC_INT_DIR_DOUBLE = 97
    ML_IS_WITHIN_DISTANCE_SQUARED__LOC_INT_DIR_DOUBLE_BOOLEAN = 98
    ML_IS_ADJACENT_TO = 99
    ML_DIRECTION_TO = 100
    UT_GET_ALL_TYPE_LOCATIONS = 101
    LOG = 102
    THROW_GAME_ACTION_EXCEPTION = 103
    THROW_EXCEPTION = 104


class CrossPlayObjectType(Enum):
    OTHER = 0
    DIRECTION = 1
    MAP_LOCATION = 2
    ROBOT_INFO = 3
    MAP_INFO = 4
    MESSAGE = 5
    UNIT_TYPE = 6
    TRAP_TYPE = 7
    TEAM = 8
    THROWN_GAME_ACTION_EXCEPTION = 9


class CrossPlayClient:
    def __init__(self):
        self.sock = None

    def connect(self):
        while True:
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect((IPC_HOST, IPC_PORT))
                break
            except ConnectionRefusedError:
                time.sleep(0.1)
            except Exception as e:
                raise CrossPlayException(f"Failed to connect to Java server: {e}")

    def send_json(self, data):
        if self.sock is None:
            raise CrossPlayException("Socket not connected")

        json_bytes = json.dumps(data).encode("utf-8")
        length = len(json_bytes)
        
        try:
            self.sock.sendall(struct.pack(">I", length))
            self.sock.sendall(json_bytes)
            # print("Json bytes sent:", struct.pack(">I", length) + json_bytes)
        except Exception as e:
            raise CrossPlayException(f"Failed to send data: {e}")

    def receive_json(self):
        if self.sock is None:
            raise CrossPlayException("Socket not connected")

        try:
            length_bytes = self.recv_exactly(4)
            length = struct.unpack(">I", length_bytes)[0]
            data_bytes = self.recv_exactly(length)
            return json.loads(data_bytes.decode("utf-8"))
        except Exception as e:
            raise CrossPlayException(f"Failed to receive data: {e}")

    def recv_exactly(self, n):
        if self.sock is None:
            raise CrossPlayException("Socket not connected")

        data = b""
        while len(data) < n:
            packet = self.sock.recv(n - len(data))
            if not packet:
                raise CrossPlayException("Socket connection closed unexpectedly")
            data += packet
        return data

    def close(self):
        if self.sock:
            self.sock.close()
            self.sock = None


# Global client instance
_client = CrossPlayClient()
# _spawn_queue: deque[tuple[Team, int]] = deque()
_destroy_queue: deque[int] = deque()


def connect():
    _client.connect()


def close():
    _client.close()


def send(method: CrossPlayMethod, params=None):
    if params is None:
        params = []

    json_params = list(map(make_json, params))
    message = {"method": method.value, "params": json_params}

    _client.send_json(message)


def send_null():
    _client.send_json(None)


def send_and_wait(method: CrossPlayMethod, params=None):
    send(method, params)
    return _client.receive_json()


directions = list(Direction)
teams = list(Team)
unit_types = list(UnitType)
trap_types = list(TrapType)
exception_types = list(GameActionExceptionType)
object_types = list(CrossPlayObjectType)

direction_index = {dir: i for i, dir in enumerate(directions)}
team_index = {team: i for i, team in enumerate(teams)}
unit_type_index = {ut: i for i, ut in enumerate(unit_types)}
trap_type_index = {tt: i for i, tt in enumerate(trap_types)}
exception_type_index = {et: i for i, et in enumerate(exception_types)}

def make_json(obj):
    match obj:
        case Direction():
            return {"type": CrossPlayObjectType.DIRECTION.value, "val": direction_index[obj]}
        case Team():
            return {"type": CrossPlayObjectType.TEAM.value, "val": team_index[obj]}
        case MapLocation():
            return {"type": CrossPlayObjectType.MAP_LOCATION.value, "x": obj.x, "y": obj.y}
        case UnitType():
            return {"type": CrossPlayObjectType.UNIT_TYPE.value, "val": unit_type_index[obj]}
        case TrapType():
            return {"type": CrossPlayObjectType.TRAP_TYPE.value, "val": trap_type_index[obj]}
        case int() | str() | float() | bool() | None:
            return obj
        case _:
            raise TypeError(f"Cannot pass an object of type {type(obj).__name__} into a Battlecode function."
                             " No Battlecode functions accept objects of this type, so the Python engine"
                             " does not support passing in this type.")


def parse(json):
    while True:  # so that as many robots as necessary can be destroyed, but usually return after one iteration
        if json is None:
            return None
        
        if isinstance(json, dict) and "type" in json:
            raw_type = json["type"]

            if raw_type == "destroy_bot":
                _destroy_queue.append(json["id"])
                send_null()
                json = _client.receive_json()
                continue
            # elif raw_type == "spawn_bot":
                # _spawn_queue.append((teams[json["team"]], json["id"]))
                # send_null()
                # _client.receive_json()  # consume the extra null response
                # return None
            elif isinstance(raw_type, int):
                obj_type = object_types[raw_type]
        
                match obj_type:
                    case CrossPlayObjectType.THROWN_GAME_ACTION_EXCEPTION:
                        raise GameActionException(exception_types[json["etype"]], json["msg"])
                    case CrossPlayObjectType.DIRECTION:
                        return directions[json["val"]]
                    case CrossPlayObjectType.TEAM:
                        return teams[json["val"]]
                    case CrossPlayObjectType.MAP_LOCATION:
                        return MapLocation(json["x"], json["y"])
                    case CrossPlayObjectType.UNIT_TYPE:
                        return unit_types[json["val"]]
                    case CrossPlayObjectType.TRAP_TYPE:
                        return trap_types[json["val"]]
                    case CrossPlayObjectType.MESSAGE:
                        return Message(json["bytes"], json["sid"], json["round"], json["loc"])
                    case CrossPlayObjectType.ROBOT_INFO:
                        return RobotInfo(
                            json["id"],
                            parse(json["team"]),
                            parse(json["ut"]),
                            json["hp"],
                            parse(json["loc"]),
                            parse(json["dir"]),
                            json["chir"],
                            json["ch"],
                            parse(json["carry"]),
                        )
                    case CrossPlayObjectType.MAP_INFO:
                        return MapInfo(
                            parse(json["loc"]),
                            json["pass"],
                            json["wall"],
                            json["dirt"],
                            json["ch"],
                            parse(json["trap"]),
                            json["cm"],
                        )
                    case _:
                        raise CrossPlayException("Unknown object type received from server: "
                                                + str(obj_type))
            else:
                raise CrossPlayException("Invalid object type received from server: "
                                        + str(raw_type))
        elif isinstance(json, list):
            return [parse(item) for item in json]
        else:
            return json


def send_wait_and_parse(method: CrossPlayMethod, params=None):
    response = send_and_wait(method, params)
    return parse(response)


def receive():
    return _client.receive_json()

