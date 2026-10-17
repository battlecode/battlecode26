import os
from argparse import ArgumentParser
from subprocess import Popen
import sys
import traceback

from .classes import GameActionException
from .runner import RobotRunner
from .crossplay import (
    BYTECODE_LIMIT,
    CrossPlayException,
    receive,
    connect,
    close,
    send,
    send_null,
    CrossPlayMethod,
    _destroy_queue,
)
from .wrappers import _GAME_METHODS, Team

DETACHED_PROCESS = 0x00000008
TEAM_NAMES = {Team.A: "A", Team.B: "B", Team.NEUTRAL: "N"}


def get_code(bot_name, bot_dir):
    if bot_name is None:
        return None

    path = f"{bot_dir}/{bot_name}"
    # read all files in this directory into a dictionary
    code = {}

    if not os.path.exists(path):
        raise ValueError(f"Bot directory '{path}' not found!")

    for filename in os.listdir(path):
        if filename.endswith(".py"):
            with open(os.path.join(path, filename), "r") as f:
                code[filename[:-3]] = f.read()

    return code


def get_error_printer(team=None, id=None, round_provider=None):
    def format_print(*args):
        team_name = "?"
        if team in TEAM_NAMES:
            team_name = TEAM_NAMES[team]
        r = round_provider() if round_provider else "?"
        print(f"[{team_name}: #{id}@{r}] ERROR: ", end="")
        print(*args)

    return format_print


def play(team_a=None, team_b=None, dir_a="src", dir_b="src", debug=False):
    if team_a == "/":
        team_a = None
    if team_b == "/":
        team_b = None

    code = {Team.A: get_code(team_a, dir_a), Team.B: get_code(team_b, dir_b), Team.NEUTRAL: None}
    active_bots: dict[int, RobotRunner] = {}
    current_round = 0
    # _spawn_queue.clear()
    _destroy_queue.clear()

    def get_round():
        return current_round

    # Connect to the crossplay server
    connect()

    try:
        while True:
            # wait for server message
            response = receive()
            
            if debug:
                print("Received message:", response)

            if not response:
                # For null responses
                continue

            msg_type = response["type"]
            # pending_spawns = len(_spawn_queue) > 0
            pending_destroys = len(_destroy_queue) > 0
            handled = False

            if msg_type == "end_game":
                handled = True

                for runner in active_bots.values():
                    runner.kill()
                    
                break

            if msg_type == "spawn_bot":  # or pending_spawns:
                # while True:  # faking a do-while loop in Python
                    # if pending_spawns:
                        # team, robot_id = _spawn_queue.popleft()
                    # else:

                handled = True
                send_null()
                team_ordinal = response["team"]
                team = Team(team_ordinal)
                robot_id = response["id"]

                if debug:
                    print(f"Spawning bot {robot_id} for team {team}")

                runner = RobotRunner(
                    code=code[team],
                    game_methods=_GAME_METHODS,
                    error_method=get_error_printer(
                        team=team, id=robot_id, round_provider=get_round
                    ),
                    bytecode_limit=BYTECODE_LIMIT,
                    debug=debug,
                )

                try:
                    runner.init_robot()
                    active_bots[robot_id] = runner
                    bytecode_used = runner.bytecode_limit - runner.bytecode
                    send(CrossPlayMethod.END_TURN, [bytecode_used])
                except CrossPlayException as e:
                    runner.kill()
                    raise e
                except GameActionException as e:
                    runner.kill()

                    if debug:
                        print(f"GameActionException during initialization of bot {robot_id}: {e}")

                    traceback.print_exc()
                    send(CrossPlayMethod.THROW_GAME_ACTION_EXCEPTION, [e.type.value, traceback.format_exc()])
                except Exception as e:
                    runner.kill()
                    
                    if debug:
                        print(f"Exception during initialization of bot {robot_id}: {e}")
                        traceback.print_exc()

                    send(CrossPlayMethod.THROW_EXCEPTION, [traceback.format_exc()])
                    
                    # pending_spawns = len(_spawn_queue) > 0

                    # if not pending_spawns:
                    #     break

            if msg_type == "destroy_bot" or pending_destroys:
                need_to_destroy_current = msg_type == "destroy_bot"

                if need_to_destroy_current:
                    handled = True

                while True:  # faking a do-while loop in Python
                    if pending_destroys:
                        robot_id = _destroy_queue.popleft()
                    else:
                        send_null()
                        robot_id = response["id"]
                        need_to_destroy_current = False

                    if debug:
                        print(f"Destroying bot {robot_id}")

                    if robot_id in active_bots:
                        runner = active_bots.pop(robot_id)
                        runner.kill()
                    
                    pending_destroys = len(_destroy_queue) > 0

                    if not need_to_destroy_current and not pending_destroys:
                        break

            if msg_type == "start_turn":
                handled = True
                current_round = response["round"]
                robot_id = response["id"]

                if debug:
                    print(f"Running turn {current_round} for id {robot_id}")

                if robot_id in active_bots:
                    runner = active_bots[robot_id]

                    try:
                        runner.run()
                        bytecode_used = runner.bytecode_limit - runner.bytecode
                        send(CrossPlayMethod.END_TURN, [bytecode_used])
                    except CrossPlayException as e:
                        runner.kill()
                        raise e
                    except GameActionException as e:
                        runner.kill()

                        if debug:
                            print(f"GameActionException during turn {current_round} of bot {robot_id}: {e}")
                            traceback.print_exc()

                        send(CrossPlayMethod.THROW_GAME_ACTION_EXCEPTION, [e.type.value, traceback.format_exc()])
                    except Exception as e:
                        runner.kill()

                        if debug:
                            print(f"Exception during turn {current_round} of bot {robot_id}: {e}")
                            traceback.print_exc()

                        send(CrossPlayMethod.THROW_EXCEPTION, [traceback.format_exc()])
                else:
                    # bot init must have thrown an exception
                    send(CrossPlayMethod.RC_DISINTEGRATE)

            if not handled:
                raise CrossPlayException(f"Unknown message type: {msg_type}")

    except KeyboardInterrupt:
        pass
    except CrossPlayException as e:
        print(f"CrossPlay runner error: {e}")
        traceback.print_exc()
    finally:
        for runner in active_bots.values():
            runner.kill()
        close()


def main(args=None):
    if args is None:
        args = sys.argv[1:]

    if sys.version_info.major != 3 or sys.version_info.minor != 12:
        print(
            f"Error: The Battlecode Python runner requires Python 3.12. Found version {sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}."
        )
        sys.exit(1)

    parser = ArgumentParser()
    parser.add_argument("--teamA", help="Name of team A Python bot, or '/' for no bot", default="/")
    parser.add_argument("--teamB", help="Name of team B Python bot, or '/' for no bot", default="/")
    parser.add_argument("--dirA", help="Directory for team A code", default="src")
    parser.add_argument("--dirB", help="Directory for team B code", default="src")
    parser.add_argument("--debug", action="store_true", help="Enable debug mode")
    parser.add_argument(
        "--new-process",
        action="store_true",
        help="Start the Python runner in a new process",
    )
    parsed_args = parser.parse_args(args)
    team_a = parsed_args.teamA
    team_b = parsed_args.teamB
    dir_a = parsed_args.dirA
    dir_b = parsed_args.dirB
    debug = parsed_args.debug

    if parsed_args.new_process:
        new_args = [
            sys.executable,
            "-m",
            "battlecode26",
            "--teamA",
            team_a,
            "--teamB",
            team_b,
            "--dirA",
            dir_a,
            "--dirB",
            dir_b,
        ]

        if debug:
            new_args.append("--debug")

        # make sure that the code is valid before starting a new process
        get_code(team_a, dir_a)
        get_code(team_b, dir_b)

        kwargs = {
            "shell": False,
            "stdin": None,
            "stdout": None,
            "stderr": None,
            "close_fds": True,
        }

        if os.name == "nt":
            kwargs["creationflags"] = DETACHED_PROCESS

        Popen(
            new_args,
            **kwargs
        )
    else:
        play(team_a=parsed_args.teamA, team_b=parsed_args.teamB,
             dir_a=parsed_args.dirA, dir_b=parsed_args.dirB,
             debug=parsed_args.debug)


if __name__ == "__main__":
    main()
