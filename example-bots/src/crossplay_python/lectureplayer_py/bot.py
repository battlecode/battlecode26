
from battlecode26 import *
import random
from enum import Enum

class State(Enum):
    INITIALIZE = 1
    FIND_CHEESE = 2
    RETURN_TO_KING = 3
    BUILD_TRAPS = 4
    EXPLORE_AND_ATTACK = 5
    RETURN_TO_KING_THEN_EXPLORE = 6

rand = random.Random(1092)

current_state = State.INITIALIZE

num_rats_spawned = 0
turns_since_carry = 1000

directions = list(Direction)

mine_loc = None
num_mines = 0
mine_locs = []

explore_when_finding_cheese = False
target_cheese_mine_loc = None

class SqueakType(Enum):
    INVALID = 0
    ENEMY_RAT_KING = 1
    ENEMY_COUNT = 2
    CHEESE_MINE = 3
    CAT_FOUND = 4

squeak_types = list(SqueakType)


def move_random():
    forward_loc = rc.adjacent_location(rc.get_direction())

    if rc.can_remove_dirt(forward_loc):
        rc.remove_dirt(forward_loc)

    if rc.can_move_forward():
        rc.move_forward()
    else:
        random_dir = directions[rand.randint(0, len(directions) - 1)]

        if rc.can_turn(random_dir):
            rc.turn(random_dir)


def run_rat_king():
    global num_rats_spawned, num_mines

    current_cost = rc.get_current_rat_cost()

    potential_spawn_locations = rc.get_all_locations_within_radius_squared(rc.get_location(), 8)
    spawn = current_cost <= 10 or rc.get_all_cheese() > current_cost + 2500

    for loc in potential_spawn_locations:
        if spawn and rc.can_build_rat(loc):
            rc.build_rat(loc)
            num_rats_spawned += 1
            break

        if rc.can_pick_up_cheese(loc):
            rc.pick_up_cheese(loc)
            break

    squeaks = rc.read_squeaks(rc.get_round_num())

    for msg in squeaks:
        raw_squeak = msg.bytes

        if get_squeak_type(raw_squeak) != SqueakType.CHEESE_MINE:
            continue

        encoded_loc = get_squeak_value(raw_squeak)

        if encoded_loc in mine_locs:
            continue

        mine_locs.append(encoded_loc)
        first_int = get_first_int(encoded_loc)
        last_int = get_last_int(encoded_loc)

        rc.write_shared_array(2 * num_mines + 2, first_int)
        rc.write_shared_array(2 * num_mines + 3, last_int)
        log("Writing to shared array:", first_int, last_int)
        log("Cheese mine located at:", get_x(encoded_loc), get_y(encoded_loc))

        num_mines += 1

    move_random()

    # update king location in shared array
    rc.write_shared_array(0, rc.get_location().x)
    rc.write_shared_array(1, rc.get_location().y)


def run_find_cheese():
    global explore_when_finding_cheese, target_cheese_mine_loc, current_state

    if not explore_when_finding_cheese and num_mines == 0:
        explore_when_finding_cheese = True

    if target_cheese_mine_loc is None and not explore_when_finding_cheese and num_mines > 0:
        cheese_mine_index = rand.randint(0, num_mines - 1)
        x = rc.read_shared_array(2 * cheese_mine_index + 2)
        y = rc.read_shared_array(2 * cheese_mine_index + 3)
        encoded_loc = 1024 * y + x
        target_cheese_mine_loc = MapLocation(get_x(encoded_loc), get_y(encoded_loc))

    nearby_infos = rc.sense_nearby_map_infos()

    for info in nearby_infos:
        if info.cheese_amount > 0:
            to_cheese = direction_to(rc.get_location(), info.location)

            if rc.can_turn(to_cheese):
                rc.turn(to_cheese)
                break
        elif info.has_cheese_mine:
            global mine_loc
            mine_loc = info.location
            log("Found cheese mine at", mine_loc)

    for dir in directions:
        loc = rc.get_location().add(dir)

        if rc.can_pick_up_cheese(loc):
            rc.pick_up_cheese(loc)

            if rc.get_raw_cheese() >= 10:
                current_state = State.RETURN_TO_KING

    if explore_when_finding_cheese:
        rc.set_indicator_string("Exploring!")
        move_random()
    elif target_cheese_mine_loc is not None:
        rc.set_indicator_string(f"Going to cheese mine at {target_cheese_mine_loc}")
        to_target = direction_to(rc.get_location(), target_cheese_mine_loc)
        next_loc = rc.get_location().add(to_target)

        if rc.can_turn(to_target):
            rc.turn(to_target)

        if rc.can_remove_dirt(next_loc):
            rc.remove_dirt(next_loc)

        if rc.can_move(to_target):
            rc.move(to_target)

        target_cheese_mine_loc = None


def run_return_to_king():
    global current_state, explore_when_finding_cheese

    king_loc = MapLocation(rc.read_shared_array(0), rc.read_shared_array(1))
    to_king = direction_to(rc.get_location(), king_loc)
    next_loc = rc.get_location().add(to_king)

    if rc.can_turn(to_king):
        rc.turn(to_king)

    if rc.can_remove_dirt(next_loc):
        rc.remove_dirt(next_loc)

    if rc.can_move(to_king):
        rc.move(to_king)

    raw_cheese = rc.get_raw_cheese()

    if raw_cheese == 0:
        current_state = State.FIND_CHEESE
        explore_when_finding_cheese = rand.choice([True, False]) and rand.choice([True, False])

    if rc.can_sense_location(king_loc):
        if distance_squared_to(king_loc, rc.get_location()) <= 16 and mine_loc is not None:
            rc.squeak(get_squeak(SqueakType.CHEESE_MINE, to_integer(mine_loc)))

        king_locations: list[RobotInfo] = rc.sense_nearby_robots(king_loc, 8, rc.get_team())

        for robot_info in king_locations:
            if robot_info.type.is_rat_king_type():
                actual_king_loc = robot_info.location

                if rc.can_transfer_cheese(actual_king_loc, raw_cheese):
                    log(f"Transferred {raw_cheese} cheese to king at {king_loc}: I'm at {rc.get_location()}")
                    rc.transfer_cheese(actual_king_loc, raw_cheese)
                    current_state = State.FIND_CHEESE
                    explore_when_finding_cheese = rand.choice([True, False]) and rand.choice([True, False])

                break


def run_build_traps():
    global current_state

    for dir in directions:
        loc = rc.get_location().add(dir)
        cat_traps = rand.choice([True, False])

        if cat_traps and rc.can_place_cat_trap(loc):
            log("Built cat trap at", loc)
            rc.place_cat_trap(loc)
        elif rc.can_place_rat_trap(loc):
            log("Built rat trap at", loc)
            rc.place_rat_trap(loc)

    if rand.random() < 0.1:
        current_state = State.EXPLORE_AND_ATTACK

    move_random()


def run_explore_and_attack():
    global current_state, turns_since_carry

    squeaks = rc.read_squeaks(rc.get_round_num())

    for msg in squeaks:
        raw_squeak = msg.bytes

        if get_squeak_type(raw_squeak) != SqueakType.CAT_FOUND:
            continue

        dir_ordinal = get_squeak_value(raw_squeak)
        to_cat = directions[dir_ordinal]
        away = to_cat.opposite()

        if rc.can_turn(away):
            rc.turn(away)
            break

        if rc.can_remove_dirt(rc.get_location().add(away)):
            rc.remove_dirt(rc.get_location().add(away))

        if rc.can_move(away):
            rc.move(away)
            break

    move_random()

    if rc.can_throw_rat() and turns_since_carry >= 3:
        rc.throw_rat()

    for dir in directions:
        loc = rc.get_location().add(dir)

        if rc.can_carry_rat(loc):
            rc.carry_rat(loc)
            turns_since_carry = 0

        if rc.can_attack(loc):
            rc.attack(loc)

    if rand.random() < 0.1:
        current_state = State.BUILD_TRAPS

    nearby_enemies = rc.sense_nearby_robots(..., rc.get_type().vision_cone_radius_squared, rc.get_team().opponent())
    nearby_cats = rc.sense_nearby_robots(..., rc.get_type().vision_cone_radius_squared, Team.NEUTRAL)

    for enemy in nearby_enemies:
        if enemy.type.is_rat_king_type():
            current_state = State.RETURN_TO_KING_THEN_EXPLORE

    num_enemies = len(nearby_enemies)
    if num_enemies > 0:
        rc.set_indicator_string(f"Nearby enemies: {num_enemies}")
        rc.squeak(get_squeak(SqueakType.ENEMY_COUNT, num_enemies))

    if len(nearby_cats) > 0:
        if distance_squared_to(rc.get_location(), nearby_cats[0].location) >= 17:
            rc.set_indicator_string(f"Found a cat at {nearby_cats[0].location}")
            to_cat = direction_to(rc.get_location(), nearby_cats[0].location)
            rc.squeak(get_squeak(SqueakType.CAT_FOUND, to_cat.ordinal()))
        else:
            rc.set_indicator_string("Cat is too close! Running away!")
            away = direction_to(rc.get_location(), nearby_cats[0].location).opposite()
            if rc.can_turn(away):
                rc.turn(away)

            if rc.can_remove_dirt(rc.get_location().add(away)):
                rc.remove_dirt(rc.get_location().add(away))

            if rc.can_move(away):
                rc.move(away)


def to_integer(loc: MapLocation) -> int:
    return (loc.x << 6) | loc.y


def get_first_int(loc: int) -> int:
    return loc % 1024


def get_last_int(loc: int) -> int:
    return loc >> 10


def get_x(encoded_loc: int) -> int:
    return encoded_loc >> 6


def get_y(encoded_loc: int) -> int:
    return encoded_loc % 64


def get_squeak(type_: SqueakType, value: int) -> int:
    if type_ == SqueakType.ENEMY_RAT_KING:
        return (1 << 12) | value
    if type_ == SqueakType.ENEMY_COUNT:
        return (2 << 12) | value
    if type_ == SqueakType.CHEESE_MINE:
        return (3 << 12) | value
    if type_ == SqueakType.CAT_FOUND:
        return (4 << 12) | value
    return value


def get_squeak_type(raw_squeak: int):
    return squeak_types[raw_squeak >> 12]


def get_squeak_value(raw_squeak: int):
    return raw_squeak % 4096


def turn():
    global rand, current_state, num_rats_spawned, turns_since_carry, \
        directions, mine_loc, num_mines, mine_locs, \
        explore_when_finding_cheese, target_cheese_mine_loc

    try:
        if rc.get_type().is_rat_king_type():
            run_rat_king()
        else:
            turns_since_carry += 1

            if current_state == State.INITIALIZE:
                if rc.get_round_num() < 30 or rc.get_current_rat_cost() <= 10:
                    current_state = State.FIND_CHEESE
                    # replicate Java randomness: two coin flips
                    explore_when_finding_cheese = rand.choice([True, False]) and rand.choice([True, False])
                else:
                    current_state = State.EXPLORE_AND_ATTACK

            elif current_state == State.FIND_CHEESE:
                run_find_cheese()
            elif current_state == State.RETURN_TO_KING:
                run_return_to_king()
            elif current_state == State.BUILD_TRAPS:
                run_build_traps()
            elif current_state == State.EXPLORE_AND_ATTACK:
                run_explore_and_attack()
            elif current_state == State.RETURN_TO_KING_THEN_EXPLORE:
                run_return_to_king()

                if current_state == State.FIND_CHEESE:
                    current_state = State.EXPLORE_AND_ATTACK

    except GameActionException as e:
        log("GameActionException in bot:")
        log(e)
    except Exception as e:
        log("Exception in bot:")
        log(e)
