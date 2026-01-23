
from battlecode26 import *
from random import randint, seed

seed(6147)

turn_count = 0

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
    global turn_count
    turn_count += 1

    if turn_count == 1:
        log("I'm alive")
        rc.set_indicator_string("Hello world!")

    try:
        if turn_count % 100 == 0:
            log("Turn " + str(turn_count) + ": I am a " + str(rc.get_type()))
        
        if rc.can_move_forward():
            log("Turn " + str(turn_count) + ": Trying to move " + str(rc.get_direction()))
            rc.move_forward()
        else:
            log("Couldn't move forward on turn " + str(turn_count) + " at location " + str(rc.get_location()) + ", " + str(rc.get_direction()))
            random_direction = randint(0, 7)

            if rc.can_turn():
                rc.turn(directions[random_direction])
    except GameActionException as e:
        log("GameActionException: " + str(e))
    except Exception as e:
        log("Exception: " + str(e))
