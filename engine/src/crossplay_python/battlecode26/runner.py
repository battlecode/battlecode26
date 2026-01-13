import sys
import traceback

from RestrictedPython import safe_builtins, limited_builtins, utility_builtins, Guards
from threading import Thread, Event, Lock
from time import sleep

import battlecode26

from .classes import GameActionException, GameActionExceptionType
from .instrument import Instrument
from types import CodeType, MethodType
from typing import Any, List
import dis
import inspect
from enum import Enum
from .wrappers import RobotController

class GameFinishedException(Exception):
    pass

class RobotThread(Thread):
    def __init__(self, runner):
        Thread.__init__(self)
        self.pause_event = Event()  # signal = unpause execution
        self.run_event = Event()  # signal = run next turn
        self.finished_event = Event()  # signal = current turn finished/paused
        self.paused = False
        self.runner = runner
        self.running = True
        self.exc = None

    def run(self):
        # Keep this thread alive until the robot is destroyed
        while self.running:
            self.run_event.wait()
            if not self.running:
                return

            try:
                #print("bytecode before", self.runner.bytecode)
                if not self.runner.initialized:
                    self.runner.init_robot()
                self.runner.do_turn()
                #print("bytecode after", self.runner.bytecode)
            except Exception as e:
                self.exc = e

            self.run_event.clear()
            self.finished_event.set()

    def wait(self):
        if not self.running:
            raise GameFinishedException
        self.paused = True
        self.finished_event.set()  # External signal that we are finished for now
        self.pause_event.wait()  # Wait for unpause
        self.pause_event.clear()
        self.paused = False

    def kill(self):
        self.running = False
        self.pause_event.set()
        self.run_event.set()

class RobotRunner:
    def __init__(self, code, game_methods, error_method, bytecode_limit, debug=False):
        self.instrument = Instrument(self)
        
        self.locals = {}
        self.globals = {
            '__builtins__': dict(i for dct in [safe_builtins, limited_builtins] for i in dct.items()),
            '__name__': '__main__'
        }
    
        self.globals['__builtins__']['range'] = range
        self.globals['__builtins__']['__metaclass__'] = type
        self.globals['__builtins__']['instrument'] = self.instrument_call
        self.globals['__builtins__']['__multinstrument__'] = self.multinstrument_call
        self.globals['__builtins__']['__import__'] = self.import_call
        self.globals['__builtins__']['_getitem_'] = self.getitem_call
        self.globals['__builtins__']['_write_'] = self.write_call
        self.globals['__builtins__']['_getiter_'] = lambda i: i
        self.globals['__builtins__']['_inplacevar_'] = self.inplacevar_call
        self.globals['__builtins__']['_unpack_sequence_'] = Guards.guarded_unpack_sequence
        self.globals['__builtins__']['_iter_unpack_sequence_'] = Guards.guarded_iter_unpack_sequence
        self.globals['__builtins__']['open'] = open
        self.globals['__builtins__']['enumerate'] = enumerate
        self.globals['__builtins__']['set'] = set
        self.globals['__builtins__']['frozenset'] = frozenset

        # instrumented methods
        self.globals['__builtins__']['sorted'] = self.instrument.instrumented_sorted

        for name, func_and_cost in game_methods.items():
            def make_wrapper(func_info):
                # if cost is provided, func_info is func, cost tuple, otherwise just the function
                if not isinstance(func_info, tuple):
                    return func_info
                func, cost = func_info
                # if not a function, (e.g. MapLocation, Direction, etc) return
                if not isinstance(func, MethodType):
                    return func

                def func_wrapper(*args, **kwargs):
                    self.multinstrument_call(cost)
                    return func(*args, **kwargs)
                return func_wrapper
            
            self.globals['__builtins__'][name] = make_wrapper(func_and_cost)

        self.error_method = error_method
        self.game_methods = game_methods
        self.code = code
        self.imports = {}

        self.bytecode = bytecode_limit
        self.bytecode_limit = bytecode_limit

        self.initialized = False
        self.debug = debug

        # Start robot worker
        self.thread = RobotThread(self)
        self.thread.start()

    @staticmethod
    def inplacevar_call(op, x, y):
        if not isinstance(op, str):
            raise SyntaxError('Unsupported in place op.')

        if op == '+=':
            return x + y

        elif op == '-=':
            return x - y

        elif op == '*=':
            return x * y

        elif op == '/=':
            return x / y

        else:
            raise SyntaxError('Unsupported in place op "' + op + '".')

    @staticmethod
    def write_call(obj):
        if isinstance(obj, type(sys)):
            raise RuntimeError('Can\'t write to modules.')

        elif isinstance(obj, type(lambda: 1)):
            raise RuntimeError('Can\'t write to functions.')

        return obj

    @staticmethod
    def getitem_call(accessed, attribute):
        if isinstance(attribute, str) and len(attribute) > 0:
            if attribute[0] == '_':
                raise RuntimeError('Cannot access attributes that begin with "_".')

        return accessed[attribute]

    def instrument_call(self):
        self.bytecode -= 1
        if self.bytecode <= 0:
            self.error_method(f'Ran out of bytecode. Remaining bytecode: {self.bytecode}')
            self.pause()

    def multinstrument_call(self, n):
        self.bytecode -= n
        if self.bytecode <= 0:
            self.error_method(f'Ran out of bytecode. Remaining bytecode: {self.bytecode}')
            self.pause()

    def import_call(self, name, globals=None, locals=None, fromlist=(), level=0, caller='robot'):
        if not isinstance(name, str) or not (isinstance(fromlist, tuple) or fromlist is None):
            raise ImportError('Invalid import.')

        if name == '':
            # This should be easy to add, but it's work.
            raise ImportError('No relative imports (yet).')

        if not name in self.code:
            if name == 'random':
                import random
                return random
            if name == 'math':
                import math
                return math
            if name == 'enum':
                import enum
                return enum
            if name == 'battlecode26':
                import battlecode26
                
                if hasattr(battlecode26, '_main'):
                    del battlecode26._main

                return battlecode26

            raise ImportError('Module "' + name + '" does not exist.')

        my_builtins = dict(self.globals['__builtins__'])
        my_builtins['__import__'] = lambda n, g, l, f, le: self.import_call(n, g, l, f, le, caller=name)
        run_globals = {'__builtins__': my_builtins, '__name__': name}

        # Loop check: keep dictionary of who imports who.  If loop, error.
        # First, we build a directed graph:
        if not caller in self.imports:
            self.imports[caller] = {name}
        else:
            self.imports[caller].add(name)

        # Next, we search for cycles.
        path = set()

        def visit(vertex):
            path.add(vertex)
            for neighbour in self.imports.get(vertex, ()):
                if neighbour in path or visit(neighbour):
                    return True
            path.remove(vertex)
            return False

        if any(visit(v) for v in self.imports):
            raise ImportError('Infinite loop in imports: ' + ", ".join(path))

        exec(self.code[name], run_globals)
        new_module = type(sys)(name)
        new_module.__dict__.update(run_globals)

        return new_module

    def init_robot(self):
        # try:
        exec(self.code['bot'], self.globals, self.locals)
        self.globals.update(self.locals)
        self.initialized = True
        # except:
        #     self.error_method(traceback.format_exc(limit=5))

    def do_turn(self):
        # print("Doing turn")
        if 'turn' in self.locals and isinstance(self.locals['turn'], type(lambda: 1)):
            # try:
            exec(self.locals['turn'].__code__, self.globals, self.locals)
            # except Exception as e:
            #     if not isinstance(e, GameFinishedException):
            #         self.error_method(traceback.format_exc(limit=5))
        else:
            # self.error_method('Couldn\'t find turn function.')
            raise SyntaxError('Couldn\'t find turn() function.'
                              ' All Battlecode bots written in Python must have a turn() function.')

    def run(self):
        self.bytecode = self.bytecode_limit

        # Kickoff execution. If we are currently paused, resume. Otherwise, signal
        # starting a new turn
        if self.thread.paused:
            self.thread.pause_event.set()
        else:
            self.thread.run_event.set()

        # Wait until the robot either pauses or completes its turn
        self.thread.finished_event.wait()
        self.thread.finished_event.clear()
        
        if self.thread.exc:
            self.thread.kill()
            raise self.thread.exc

    def pause(self):
        self.thread.wait()

    def kill(self):
        self.thread.kill()
        self.thread.join()
