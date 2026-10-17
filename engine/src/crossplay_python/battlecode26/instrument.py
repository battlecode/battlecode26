import dis
import math
from types import CodeType

import dis
from types import SimpleNamespace

class Instruction(SimpleNamespace):
    def __init__(self, instruction, in_dict=None):
        if in_dict is not None:
            super().__init__(**in_dict)
        else:
            super().__init__(**{a:b for a,b in zip(dis.Instruction._fields+('jump_to', 'was_there', 'extra_extended_args'), instruction + (None, True, 0))})

    def is_jumper(self):
        return self.is_abs_jumper() or self.is_rel_jumper()

    def is_rel_jumper(self):
        return self.opcode in dis.hasjrel

    def is_abs_jumper(self):
        return self.opcode in dis.hasjabs

    @classmethod
    def ExtendedArgs(self, value):
        return Instruction(None, in_dict={
            'opcode':144, 'opname':'EXTENDED_ARGS', 'arg':value,
            'argval':value, 'argrepr':value, 'offset':None,
            'starts_line':None, 'is_jump_target':False, 'was_there': False,
            'extra_extended_args': 0,
        })

    def calculate_offset(self, instructions):
        # Return the offset (rel or abs) to self.jump_to in instructions

        # The source of a jump is the first instruction after this instruction that is not a cache instruction
        starting_loc = instructions.index(self) + 1
        while starting_loc < len(instructions) and instructions[starting_loc].opcode == 0:
            starting_loc += 1

        # The target of a jump is the first extended args instruction corresponding to the instruction we want to jump to.
        target_loc = instructions.index(self.jump_to) - self.jump_to.extra_extended_args

        if self.is_abs_jumper():
            return target_loc

        # Compute the offset from the start instruction to the target instruction. If backward jump, return the negation.
        # To make this more robust, we should have a better way of checking for a backward jump, as there are some other variants
        # of this instruction.
        offset = target_loc - starting_loc
        if self.opname == "JUMP_BACKWARD":
            return -offset
        return offset

class Instrument:

    #Do not add the bytecode counting injection immediately after any of these instructions
    EXCLUDE_INSTRUCTIONS = ["CACHE", "JUMP_FORWARD", "POP_JUMP_IF_FALSE", "POP_JUMP_IF_TRUE", "LOAD_GLOBAL", "COPY", "LOAD_FAST", "STORE_FAST", "GET_AWAITABLE", "KW_NAMES", "EXTENDED_ARG", "RESUME"]
    EXCLUDE_OPCODES = [dis.opmap[name] for name in EXCLUDE_INSTRUCTIONS]

    """
    A class for instrumenting specific methods (e.g. sort) as well as instrumenting competitor code
    """
    def __init__(self, runner):
        self.runner = runner

    def instrumented_sorted(self, iterable, key=None, reverse=False):
        cost = len(iterable) * int(math.log(len(iterable)))
        self.runner.multinstrument_call(cost)
        if not key and not reverse:
            return sorted(iterable)
        elif not reverse:
            return sorted(iterable, key=key)
        elif not key:
            return sorted(iterable, reverse=reverse)
        return sorted(iterable, key=key, reverse=reverse)

    @staticmethod
    def instrument(bytecode: CodeType):
        """
        The primary method of instrumenting code, which involves injecting a bytecode counter between every instruction to be executed

        :param bytecode: a code object, the bytecode submitted by the player
        :return: a new code object that has been injected with our bytecode counter
        """

        # Recursively instrument functions or anything else that points to a separate code object
        new_consts = []
        for i, constant in enumerate(bytecode.co_consts):
            if type(constant) == CodeType:
                new_consts.append(Instrument.instrument(constant))
            else:
                new_consts.append(constant)
        new_consts = tuple(new_consts)

        instructions = list(dis.get_instructions(bytecode, show_caches=True))

        # We wrap each instruction in our own instruction class, which adds the jump_to and was_there fields that we will use later
        for i, instruction in enumerate(instructions):
            instructions[i] = Instruction(instruction)

        # For each jump instruction, cache a reference to the instruction that it jumps to before we start modifying the bytecode
        for i, instruction in enumerate(instructions):
            if not instruction.is_jumper():
                continue
            target = [t for t in instructions if instruction.argval == t.offset][0]
            instruction.jump_to = target
            # If any targets jump to themselves, that's not kosher.
            if instruction == target:
                raise SyntaxError('No self-referential loops.')

        # We will append the instrument function as the last global in the co_names list, so this is its index.
        instrument_name_index = len(bytecode.co_names)
        # The injection, which consists of a function call to an __instrument__ method which increments bytecode
        # these instructions will be inserted between every line of instrumented code
        # As of Python 3.11, there are cache instructions after certain instructions. Each opcode has a specific number of cache instructions
        # that should go after it.
        injection = [
            dis.Instruction(opcode=2, opname='PUSH_NULL', arg=0, argval=0, argrepr=0, offset=None, starts_line=None, is_jump_target=False),
            dis.Instruction(opcode=101, opname='LOAD_NAME', arg=instrument_name_index%256, argval='__instrument__', argrepr='__instrument__', offset=None, starts_line=None, is_jump_target=False),
            dis.Instruction(opcode=171, opname='CALL', arg=0, argval=0, argrepr=0, offset=None, starts_line=None, is_jump_target=False),
            dis.Instruction(opcode=0, opname='CACHE', arg=0, argval=0, argrepr=0, offset=None, starts_line=None, is_jump_target=False),
            dis.Instruction(opcode=0, opname='CACHE', arg=0, argval=0, argrepr=0, offset=None, starts_line=None, is_jump_target=False),
            dis.Instruction(opcode=0, opname='CACHE', arg=0, argval=0, argrepr=0, offset=None, starts_line=None, is_jump_target=False),
            dis.Instruction(opcode=1, opname='POP_TOP', arg=None, argval=None, argrepr=None, offset=None, starts_line=None, is_jump_target=False)
        ]
        #extends the opargs so that it can store the index of __instrument__
        while instrument_name_index > 255: #(255 = 2^8 -1 = 1 oparg)
            instrument_name_index >>= 8
            injection = [
                dis.Instruction(
                    opcode=144,
                    opname='EXTENDED_ARG',
                    arg=instrument_name_index%256,
                    argval=instrument_name_index%256,
                    argrepr=instrument_name_index%256,
                    offset=None,
                    starts_line=None,
                    is_jump_target=False
                )
            ] + injection

        # We inject the injection after every instruction (excluding under certain conditions)
        cur_index = -1
        for (cur, last) in zip(instructions[:], [None]+instructions[:-1]):
            cur_index += 1
            #Do not add the injection as the first instruction
            #Do not add the injection such that it will displace a cache instruction, because this will separate the cache instruction
            #from the main instruction that it is tied to
            #Do not add the injection after an instruction in the excluded opcodes list (which all break the bytecode for various reasons)
            if cur_index == 0 or cur.opcode == dis.opmap["CACHE"] or last.opcode in Instrument.EXCLUDE_OPCODES:
                continue
            for j, inject in enumerate(injection):
                injected_instruction = Instruction(inject)
                injected_instruction.was_there = False # keeping track of the instructions added by us
                instructions.insert(cur_index + j, injected_instruction)
            cur_index += len(injection)

        # Iterate through instructions. If it's a jumper, calculate the new correct offset. For each new offset, if it
        # is too large to fit in the current number of EXTENDED_ARGS, inject a new EXTENDED_ARG before it. If you never
        # insert a new EXTENDED_ARGS, break out of the loop.
        fixed = False
        while not fixed:
            fixed = True

            i = 0
            for instruction in instructions[:]:
                instruction.offset = 2 * i

                if not instruction.is_jumper():
                    i += 1
                    continue

                correct_offset = instruction.calculate_offset(instructions)
                instruction.arg = correct_offset % 256
                correct_offset >>= 8

                extended_args = 0
                while correct_offset > 0:
                    # Check if there is already an EXTENDED_ARGS behind
                    if i > extended_args and instructions[i - extended_args - 1].opcode == 144:
                        instructions[i - extended_args - 1].arg = correct_offset % 256

                    # Otherwise, insert a new one
                    else:
                        instructions.insert(i, Instruction.ExtendedArgs(correct_offset % 256))
                        instruction.extra_extended_args += 1
                        i += 1
                        fixed = False

                    correct_offset >>= 8
                    extended_args += 1
                i += 1


        # linetable = bytecode.co_linetable
        # firstlineno = bytecode.co_firstlineno
        # lines = [i for i in Instrument.parse_location_table(firstlineno, linetable)]

        # linetable_2 = Instrument.write_location_table(firstlineno, lines)
        # lines_2 = [i for i in Instrument.parse_location_table(firstlineno, linetable_2)]

        # print(lines)
        # print(lines_2)
        # print()

        # lines = [i for i in bytecode.co_lines()]
        # firstlineno = bytecode.co_firstlineno

        # print(", ".join(hex(b) for b in bytearray(bytecode.co_linetable)))

        # print([i for i in Instrument.parse_location_table(firstlineno, bytecode.co_linetable)])

        # linetable_2 = Instrument.write_location_table(firstlineno, lines)

        # bytecode_2 = CodeType(bytecode.co_argcount,
        #                 bytecode.co_posonlyargcount,
        #                 bytecode.co_kwonlyargcount,
        #                 bytecode.co_nlocals,
        #                 bytecode.co_stacksize + 100,
        #                 bytecode.co_flags,
        #                 bytecode.co_code,
        #                 bytecode.co_consts,
        #                 bytecode.co_names,
        #                 bytecode.co_varnames,
        #                 bytecode.co_filename,
        #                 bytecode.co_name,
        #                 bytecode.co_qualname,
        #                 bytecode.co_firstlineno,
        #                 linetable_2,
        #                 bytecode.co_exceptiontable,
        #                 bytecode.co_freevars,
        #                 bytecode.co_cellvars)

        # lines_2 = [i for i in bytecode_2.co_lines()]
        # print(lines)
        # print(lines_2)
        # print()


        #Maintaining correct line info ( traceback bug fix)
        #co_lnotab stores line information in Byte form
        # It stores alterantively, the number of instructions to the next increase in line number and
        # the increase in line number then
        #We need to ensure that these are bytes (You might want to break an increase into two see the article or code below)
        #The code did not update these bytes, we need to update the number of instructions before the beginning of each line
        #It should be similar to the way the jump to statement were fixed, I tried to mimick them but failed, I feel like I do not inderstand instruction.py
        # I am overestimating the number of instructions before the start of the line in this fix
        # you might find the end of this article helpful: https://towardsdatascience.com/understanding-python-bytecode-e7edaae8734d

        bytecode_to_line = {} #stores the old right info in a more usefull way (maps instruction num to line num)
        for bytecode_start, bytecode_end, line in bytecode.co_lines():
            for i in range(bytecode_start, bytecode_end, 2):
                bytecode_to_line[i] = line

        #Construct a map from old instruction numbers, to new ones.
        num_injected = 0
        instruction_index = 0
        old_to_new_byte = {}
        for instruction in instructions:
            if instruction.was_there:
                old_to_new_byte[2 * (instruction_index - num_injected)] = 2 * instruction_index
            instruction_index += 1
            if not instruction.was_there:
                num_injected += 1
        
        new_bytecode_to_line = {}
        for key in bytecode_to_line:
            new_bytecode_to_line[old_to_new_byte[key]] = bytecode_to_line[key]

        #Creating a differences list of integers, while ensuring integers in it are bytes
        pairs = sorted(new_bytecode_to_line.items())
        new_co_lines = []
        bytecode_start = 0
        current_line = 0
        for i, pair in enumerate(pairs):
            if i == 0:
                current_line = pair[1]
                continue
            if pair[1] != current_line:
                new_co_lines.append((bytecode_start, pair[0], current_line))
                bytecode_start = pair[0]
                current_line = pair[1]
        if len(pairs) > 0:
            new_co_lines.append((bytecode_start, pairs[-1][0] + 2, current_line))

        # print([i for i in bytecode.co_lines()])
        # print(bytecode_to_line)
        # print(new_bytecode_to_line)
        # print(pairs)
        # print(new_co_lines)
        # print()
            
        #tranfer to bytes and we are good :)
        new_linetable = Instrument.write_location_table(bytecode.co_firstlineno, new_co_lines)


        # print("instruction list after instrument:")
        # for i in instructions:
        #     print(i.opname, i.arg, i.was_there)
        # print()

        # Finally, we repackage up our instructions into a byte string and use it to build a new code object
        byte_array = [[inst.opcode, 0 if inst.arg is None else inst.arg % 256] for inst in instructions]
        new_code = bytes(sum(byte_array, []))

        # Make sure our code can locate the __instrument__ call
        new_names = tuple(bytecode.co_names) + ('instrument',)
        compiled = Instrument.build_code(bytecode, new_code, new_names, new_consts, new_linetable)

        # print("After instrument. function:", compiled.co_name, ", names list:", compiled.co_names, "varnames:", compiled.co_varnames)

        return compiled
    
    @staticmethod
    def read(it):
        return next(it)

    @staticmethod
    def read_varint(it):
        b = Instrument.read(it)
        val = b & 63
        shift = 0
        while b & 64:
            b = Instrument.read(it)
            shift += 6
            val |= (b&63) << shift
        return val

    @staticmethod
    def read_signed_varint(it):
        uval = Instrument.read_varint(it)
        if uval & 1:
            return -(uval >> 1)
        else:
            return uval >> 1

    @staticmethod
    def parse_location_table(firstlineno, linetable):
        line = firstlineno
        addr = 0
        it = iter(linetable)
        while True:
            try:
                first_byte = Instrument.read(it)
            except StopIteration:
                return
            code = (first_byte >> 3) & 15
            length = ((first_byte & 7) + 1) * 2
            end_addr = addr + length
            if code == 15:
                yield addr, end_addr, None
                addr = end_addr
                continue
            elif code == 14: # Long form
                line_delta = Instrument.read_signed_varint(it)
                line += line_delta
                end_line = line + Instrument.read_varint(it)
                col = Instrument.read_varint(it)
                end_col = Instrument.read_varint(it)
            elif code == 13: # No column
                line_delta = Instrument.read_signed_varint(it)
                line += line_delta
            elif code in (10, 11, 12): # new line
                line_delta = code - 10
                line += line_delta
                column = Instrument.read(it)
                end_column = Instrument.read(it)
            else:
                assert (0 <= code < 10)
                second_byte = Instrument.read(it)
                column = code << 3 | (second_byte >> 4)
            yield addr, end_addr, line
            addr = end_addr
        
    @staticmethod
    def write_varint(val):
        """Encodes an integer as a varint."""
        out = bytearray()
        while True:
            byte = val & 63
            val >>= 6
            if val:
                out.append(byte | 64)
            else:
                out.append(byte)
                break
        return bytes(out)

    @staticmethod
    def write_signed_varint(val):
        """Encodes a signed integer as a signed varint."""
        uval = (-val << 1 | 1) if val < 0 else (val << 1)
        return Instrument.write_varint(uval)
    
    @staticmethod
    def write_location_table(firstlineno, table):
        """
        Converts a list of tuples into the original linetable bytes.

        Args:
            firstlineno (int): Starting line number.
            table (list of tuples): The parsed table as a list of (addr, end_addr, line).

        Returns:
            bytes: Encoded linetable bytes.
        """
        line = firstlineno
        linetable = bytearray()

        new_table = []
        for addr_start, addr_end, current_line in table:
            start = addr_start
            while start < addr_end:
                new_table.append((start, min(start + 8, addr_end), current_line))
                start += 8

        # print(new_table)

        for i, (addr_start, addr_end, current_line) in enumerate(new_table):
            length = addr_end - addr_start
            assert length >= 1 and length <= 8, "Invalid length for encoding"

            if current_line is None:
                first_byte = (15 << 3) | (length // 2 - 1)
                linetable.append(first_byte)
                continue

            line_delta = current_line - line
            first_byte = 128 | (13 << 3) | (length // 2 - 1)
            linetable.append(first_byte)
            linetable.extend(Instrument.write_signed_varint(line_delta))

            # 0110 1001
            
            line = current_line

        # print(", ".join(hex(b) for b in linetable))
        return bytes(linetable)

    @staticmethod
    def build_code(old_code: CodeType, new_code, new_names, new_consts, new_lnotab):
        """Helper method to build a new code object because Python does not allow us to modify existing code objects"""
        return CodeType(old_code.co_argcount,
                        old_code.co_posonlyargcount,
                        old_code.co_kwonlyargcount,
                        old_code.co_nlocals,
                        old_code.co_stacksize + 100, #Ensure that stack is large enough for extra calls due to instrumentation,
                        old_code.co_flags & 0xFFFE, #Disables Python's optimized locals feature, which causes instrumented code to fail when bot.py has multiple functions,
                        new_code,
                        new_consts,
                        new_names,
                        old_code.co_varnames,
                        old_code.co_filename,
                        old_code.co_name,
                        old_code.co_qualname,
                        old_code.co_firstlineno,
                        new_lnotab,
                        old_code.co_exceptiontable,
                        old_code.co_freevars,
                        old_code.co_cellvars)
