import os
import json
import time
from typing import override

from crossplay_python.classes import Team, GameActionException

from enum import Enum

BYTECODE_LIMIT = 5800
MESSAGE_DIR = "crossplay_temp"
MESSAGE_FILE_JAVA = "messages_java.json"
MESSAGE_FILE_OTHER = "messages_other.json"
LOCK_FILE_JAVA = "lock_java.txt"
LOCK_FILE_OTHER = "lock_other.txt"
STARTED_FILE_JAVA = "started_java.txt"
STARTED_FILE_OTHER = "started_other.txt"

class CrossPlayException(Exception):
    def __init__(self, message):
        super().__init__(message + " (If you are a competitor, please report this to the Battlecode staff."
                         " This is not an error in your code.)")

class CrossPlayObjectType(Enum):
    INVALID = 0
    CALL = 1
    NULL = 2
    INTEGER = 3
    STRING = 4
    BOOLEAN = 5
    DOUBLE = 6
    ARRAY = 7
    DIRECTION = 8
    MAP_LOCATION = 9
    MESSAGE = 10
    ROBOT_CONTROLLER = 11
    ROBOT_INFO = 12
    TEAM = 13
    THROWN_GAME_ACTION_EXCEPTION = 14
    # TODO add more types

class CrossPlayMethod(Enum):
    INVALID = 0
    START_TURN = 1 # returns [rc, round, team, id, end]
    END_TURN = 2 # params: [bytecode_used]
    RC_GET_ROUND_NUM = 3
    RC_GET_MAP_WIDTH = 4
    RC_GET_MAP_HEIGHT = 5
    RC_IS_COOPERATION = 6
    RC_GET_ID = 7
    RC_GET_TEAM = 8
    RC_GET_LOCATION = 9
    RC_GET_ALL_PART_LOCATIONS = 10
    RC_GET_DIRECTION = 11
    RC_GET_HEALTH = 12
    RC_GET_RAW_CHEESE = 13
    RC_GET_GLOBAL_CHEESE = 14
    RC_GET_ALL_CHEESE = 15
    RC_GET_DIRT = 16
    RC_GET_TYPE = 17
    RC_GET_CARRYING = 18
    RC_IS_BEING_THROWN = 19
    RC_IS_BEING_CARRIED = 20
    RC_ON_THE_MAP = 21
    RC_CAN_SENSE_LOCATION = 22
    RC_IS_LOCATION_OCCUPIED = 23
    RC_CAN_SENSE_ROBOT_AT_LOCATION = 24
    RC_SENSE_ROBOT_AT_LOCATION = 25
    RC_CAN_SENSE_ROBOT = 26
    RC_SENSE_ROBOT = 27
    RC_SENSE_NEARBY_ROBOTS = 28
    RC_SENSE_NEARBY_ROBOTS__INT = 29
    RC_SENSE_NEARBY_ROBOTS__INT_TEAM = 30
    RC_SENSE_NEARBY_ROBOTS__LOC_INT_TEAM = 31
    RC_SENSE_PASSABILITY = 32
    RC_SENSE_MAP_INFO = 33
    RC_SENSE_NEARBY_MAP_INFOS = 34
    RC_SENSE_NEARBY_MAP_INFOS__INT = 35
    RC_SENSE_NEARBY_MAP_INFOS__LOC = 36
    RC_SENSE_NEARBY_MAP_INFOS__LOC_INT = 37
    RC_ADJACENT_LOCATION = 38
    RC_GET_ALL_LOCATIONS_WITHIN_RADIUS_SQUARED = 39
    RC_IS_ACTION_READY = 40
    RC_GET_ACTION_COOLDOWN_TURNS = 41
    RC_IS_MOVEMENT_READY = 42
    RC_IS_TURNING_READY = 43
    RC_GET_MOVEMENT_COOLDOWN_TURNS = 44
    RC_GET_TURNING_COOLDOWN_TURNS = 45
    RC_CAN_MOVE_FORWARD = 46
    RC_CAN_MOVE = 47
    RC_MOVE_FORWARD = 48
    RC_MOVE = 49
    RC_CAN_TURN = 50
    RC_TURN = 51
    RC_GET_CURRENT_RAT_COST = 52
    RC_CAN_BUILD_ROBOT = 53
    RC_BUILD_ROBOT = 54
    RC_CAN_BECOME_RAT_KING = 55
    RC_BECOME_RAT_KING = 56
    RC_CAN_PLACE_DIRT = 57
    RC_PLACE_DIRT = 58
    RC_CAN_REMOVE_DIRT = 59
    RC_REMOVE_DIRT = 60
    RC_CAN_PLACE_RAT_TRAP = 61
    RC_PLACE_RAT_TRAP = 62
    RC_CAN_REMOVE_RAT_TRAP = 63
    RC_REMOVE_RAT_TRAP = 64
    RC_CAN_PLACE_CAT_TRAP = 65
    RC_PLACE_CAT_TRAP = 66
    RC_CAN_REMOVE_CAT_TRAP = 67
    RC_REMOVE_CAT_TRAP = 68
    RC_CAN_PICK_UP_CHEESE = 69
    RC_PICK_UP_CHEESE = 70
    RC_CAN_ATTACK = 71
    RC_CAN_ATTACK__LOC_INT = 72
    RC_ATTACK = 73
    RC_ATTACK__LOC_INT = 74
    RC_SQUEAK = 75
    RC_READ_SQUEAKS = 76
    RC_WRITE_SHARED_ARRAY = 77
    RC_READ_SHARED_ARRAY = 78
    RC_WRITE_PERSISTENT_ARRAY = 79
    RC_READ_PERSISTENT_ARRAY = 80
    RC_CAN_TRANSFER_CHEESE = 81
    RC_TRANSFER_CHEESE = 82
    RC_THROW_RAT = 83
    RC_CAN_THROW_RAT = 84
    RC_DROP_RAT = 85
    RC_CAN_DROP_RAT = 86
    RC_CAN_CARRY_RAT = 87
    RC_CARRY_RAT = 88
    RC_DISINTEGRATE = 89
    RC_RESIGN = 90
    RC_SET_INDICATOR_STRING = 91
    RC_SET_INDICATOR_DOT = 92
    RC_SET_INDICATOR_LINE = 93
    RC_SET_TIMELINE_MARKER = 94
    LOG = 95

class CrossPlayObject:
    def __init__(self, object_type):
        self.object_type = object_type

    def __str__(self):
        return f"CrossPlayObject(type={self.object_type})"
    
    def to_json(self):
        return {"type": self.object_type.value}
    
    @classmethod
    def from_json(cls, json_data):
        if "value" in json_data:
            return CrossPlayLiteral.from_json(json_data)
        elif "oid" in json_data:
            return CrossPlayReference.from_json(json_data)
        elif json_data["type"] == CrossPlayObjectType.CALL.value:
            return CrossPlayMessage.from_json(json_data)
        else:
            raise CrossPlayException(f"Cannot decode CrossPlayObject from json: {json_data}")

class CrossPlayReference(CrossPlayObject):
    def __init__(self, object_type, object_id):
        super().__init__(object_type)
        self.object_id = object_id

    @override
    def __str__(self):
        return f"CrossPlayReference(type={self.object_type}, oid={self.object_id})"
    
    def to_json(self):
        json_data = super().to_json()
        json_data["oid"] = self.object_id
        return json_data
    
    @classmethod
    def from_json(cls, json_data):
        object_type = CrossPlayObjectType(json_data["type"])
        object_id = json_data["oid"]
        return CrossPlayReference(object_type, object_id)
    
class CrossPlayLiteral(CrossPlayObject):
    def __init__(self, object_type, value):
        super().__init__(object_type)
        self.value = value
    
    @override
    def __str__(self):
        return f"CrossPlayLiteral(type={self.object_type}, value={self.value})"
    
    def reduce_literal(self):
        match self.object_type:
            case CrossPlayObjectType.INTEGER:
                return int(self.value)
            case CrossPlayObjectType.STRING:
                return str(self.value)
            case CrossPlayObjectType.BOOLEAN:
                return bool(self.value)
            case CrossPlayObjectType.DOUBLE:
                return float(self.value)
            case CrossPlayObjectType.NULL:
                return None
            case CrossPlayObjectType.ARRAY:
                arr = []

                for item in self.value:
                    if isinstance(item, CrossPlayLiteral):
                        arr.append(item.reduce_literal())
                    elif isinstance(item, CrossPlayReference):
                        arr.append(item.object_id)
                    else:
                        raise CrossPlayException(f"Cannot reduce item of type {type(item)} in CrossPlayLiteral array.")

                return arr
            case CrossPlayObjectType.MAP_LOCATION:
                # represent map locations as simple (x, y) tuples in Python
                if self.value is None:
                    return None
                if isinstance(self.value, (list, tuple)):
                    return (int(self.value[0]), int(self.value[1]))
                if isinstance(self.value, dict):
                    return (int(self.value.get('x')), int(self.value.get('y')))
                return self.value
            case CrossPlayObjectType.TEAM:
                return Team(self.value)
            case CrossPlayObjectType.THROWN_GAME_ACTION_EXCEPTION:
                raise GameActionException(str(self.value))
            case _:
                raise CrossPlayException(f"Cannot reduce CrossPlayLiteral of type {self.object_type} to primitive.")

    def to_json(self):
        json_data = super().to_json()
        
        match self.object_type:
            case CrossPlayObjectType.INTEGER:
                json_data["value"] = int(self.value)
            case CrossPlayObjectType.STRING:
                json_data["value"] = str(self.value)
            case CrossPlayObjectType.BOOLEAN:
                json_data["value"] = bool(self.value)
            case CrossPlayObjectType.DOUBLE:
                json_data["value"] = float(self.value)
            case CrossPlayObjectType.NULL:
                json_data["value"] = 0
            case CrossPlayObjectType.ARRAY:
                json_data["value"] = [item.to_json() for item in self.value]
            case CrossPlayObjectType.MAP_LOCATION:
                # represent map location as object with x/y
                if self.value is None:
                    json_data["value"] = None
                elif isinstance(self.value, (list, tuple)):
                    json_data["value"] = {"x": int(self.value[0]), "y": int(self.value[1])}
                elif isinstance(self.value, dict):
                    json_data["value"] = {"x": int(self.value.get('x')), "y": int(self.value.get('y'))}
                else:
                    try:
                        json_data["value"] = {"x": int(self.value.x), "y": int(self.value.y)}
                    except Exception:
                        json_data["value"] = None
            case CrossPlayObjectType.TEAM:
                json_data["value"] = self.value.value
            case CrossPlayObjectType.THROWN_GAME_ACTION_EXCEPTION:
                json_data["value"] = str(self.value)
            case _:
                raise CrossPlayException(f"Cannot encode CrossPlayLiteral of type {self.object_type} to json.")

        return json_data
    
    @classmethod
    def from_json(cls, json_data):
        # print(f"Parsing CrossPlayLiteral from json: {json_data}")
        object_type = CrossPlayObjectType(json_data["type"])
        
        match object_type:
            case CrossPlayObjectType.INTEGER:
                value = int(json_data["value"])
            case CrossPlayObjectType.STRING:
                value = str(json_data["value"])
            case CrossPlayObjectType.BOOLEAN:
                value = bool(json_data["value"])
            case CrossPlayObjectType.DOUBLE:
                value = float(json_data["value"])
            case CrossPlayObjectType.NULL:
                value = None
            case CrossPlayObjectType.ARRAY:
                value = [CrossPlayObject.from_json(item) for item in json_data["value"]]
            case CrossPlayObjectType.MAP_LOCATION:
                v = json_data.get("value")
                if v is None:
                    value = None
                elif isinstance(v, dict) and "x" in v and "y" in v:
                    value = (int(v["x"]), int(v["y"]))
                elif isinstance(v, (list, tuple)) and len(v) >= 2:
                    value = (int(v[0]), int(v[1]))
                else:
                    value = v
            case CrossPlayObjectType.TEAM:
                value = Team(json_data["value"])
            case CrossPlayObjectType.THROWN_GAME_ACTION_EXCEPTION:
                value = str(json_data["value"])
            case _:
                raise CrossPlayException(f"Cannot decode CrossPlayObject of type {object_type} as a literal.")

        return CrossPlayLiteral(object_type, value)

class CrossPlayMessage(CrossPlayObject):
    def __init__(self, method, params):
        super().__init__(CrossPlayObjectType.CALL)
        self.method = method
        self.params = params

    @override
    def __str__(self):
        return f"CrossPlayMessage(method={self.method}, params={self.params})"
    
    def to_json(self):
        json_data = super().to_json()
        json_data["method"] = self.method.value
        json_data["params"] = [param.to_json() for param in self.params]
        return json_data
    
    @classmethod
    def from_json(cls, json_data):
        if json_data["type"] != CrossPlayObjectType.CALL.value:
            raise CrossPlayException("Tried to parse non-call as CrossPlayMessage!")

        method = CrossPlayMethod(json_data["method"])
        params = [CrossPlayObject.from_json(param) for param in json_data["params"]]
        return CrossPlayMessage(method, params)

# 0.1 ms timestep, 10 min timeout
def wait(message: CrossPlayMessage, timeout=600, timestep=0.0001, message_dir=MESSAGE_DIR):
    try:
        read_file = os.path.join(message_dir, MESSAGE_FILE_JAVA)
        write_file = os.path.join(message_dir, MESSAGE_FILE_OTHER)
        java_lock_file = os.path.join(message_dir, LOCK_FILE_JAVA)
        other_lock_file = os.path.join(message_dir, LOCK_FILE_OTHER)

        # if directory does not exist, create it
        if not os.path.exists(message_dir):
            os.makedirs(message_dir)

        json_message = message.to_json()
        time_limit = time.time() + timeout

        # print(f"Waiting to send message Python -> Java: {json_message}")

        while os.path.exists(read_file) or os.path.exists(write_file) or os.path.exists(java_lock_file):
            time.sleep(timestep)

            if time.time() > time_limit:
                raise CrossPlayException("Cross-play message passing timed out (Python waiting, Java busy).")

        if not os.path.exists(other_lock_file):
            with open(other_lock_file, 'x') as f:
                f.write('')

            # print("Created other lock file")

        with open(write_file, 'w') as f:
            json.dump(json_message, f)

        if os.path.exists(other_lock_file):
            os.remove(other_lock_file)

        # print(f"Sent message Python -> Java: {json_message}")
        # print("Waiting for response Java -> Python...")
        time_limit = time.time() + timeout
        
        while not os.path.exists(read_file) or os.path.exists(write_file) or os.path.exists(java_lock_file):
            time.sleep(timestep)

            if time.time() > time_limit:
                raise CrossPlayException("Cross-play message passing timed out (Python waiting, Java not responding).")

        if not os.path.exists(other_lock_file):
            with open(other_lock_file, 'x') as f:
                f.write('')

        with open(read_file, 'r') as f:
            json_data = json.load(f)
            result = CrossPlayObject.from_json(json_data)
        
        os.remove(read_file)
        
        if os.path.exists(other_lock_file):
            os.remove(other_lock_file)

        # print(f"Received message Java -> Python: {result}")

        if isinstance(result, CrossPlayLiteral):
            return result.reduce_literal()
        else:
            return result
    except IOError as e:
        raise CrossPlayException("Cross-play message passing failed due to file I/O error: " + str(e))

def reset_files(message_dir=MESSAGE_DIR):
    read_file = os.path.join(message_dir, MESSAGE_FILE_JAVA)
    write_file = os.path.join(message_dir, MESSAGE_FILE_OTHER)
    java_lock_file = os.path.join(message_dir, LOCK_FILE_JAVA)
    other_lock_file = os.path.join(message_dir, LOCK_FILE_OTHER)
    java_started_file = os.path.join(message_dir, STARTED_FILE_JAVA)
    other_started_file = os.path.join(message_dir, STARTED_FILE_OTHER)

    if not os.path.exists(message_dir) or not os.path.isdir(message_dir):
        os.makedirs(message_dir)
    elif os.path.exists(other_started_file):
        print("DEBUGGING: Detected existing crossplay_temp/started_other.txt file. " \
              "This indicates that a previous cross-play match did not terminate cleanly. " \
                "Deleting the old crossplay_temp files.")
    elif os.path.exists(java_started_file):
        print("DEBUGGING: Java cross-play runner already started. Using existing cross-play temp directory.")
        return

    if os.path.exists(read_file):
        os.remove(read_file)
    
    if os.path.exists(write_file):
        os.remove(write_file)
    
    if os.path.exists(java_lock_file):
        os.remove(java_lock_file)
    
    if os.path.exists(other_lock_file):
        os.remove(other_lock_file)
    
    if not os.path.exists(os.path.join(message_dir, STARTED_FILE_OTHER)):
        with open(other_started_file, 'x') as f:
            f.write('')

def clear_temp_files(message_dir=MESSAGE_DIR):
    if not os.path.exists(message_dir) or not os.path.isdir(message_dir):
        return

    read_file = os.path.join(message_dir, MESSAGE_FILE_JAVA)
    write_file = os.path.join(message_dir, MESSAGE_FILE_OTHER)
    java_lock_file = os.path.join(message_dir, LOCK_FILE_JAVA)
    other_lock_file = os.path.join(message_dir, LOCK_FILE_OTHER)
    java_started_file = os.path.join(message_dir, STARTED_FILE_JAVA)
    other_started_file = os.path.join(message_dir, STARTED_FILE_OTHER)

    if os.path.exists(read_file):
        os.remove(read_file)
    
    if os.path.exists(write_file):
        os.remove(write_file)
    
    if os.path.exists(java_lock_file):
        os.remove(java_lock_file)
    
    if os.path.exists(other_lock_file):
        os.remove(other_lock_file)
    
    if os.path.exists(java_started_file):
        os.remove(java_started_file)
    
    if os.path.exists(other_started_file):
        os.remove(other_started_file)
