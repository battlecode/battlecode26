
from battlecode26 import *

directions = [
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST,
]

def turn():
    round_num_test = rc.get_round_num()
    map_width_test = rc.get_map_width()
    map_height_test = rc.get_map_height()

    log(f"Round num test: {round_num_test}")
    log(f"Map width test: {map_width_test}")
    log(f"Map height test: {map_height_test}")
