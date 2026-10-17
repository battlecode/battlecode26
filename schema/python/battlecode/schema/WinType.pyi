from __future__ import annotations

import flatbuffers
import numpy as np

import flatbuffers
import typing

uoffset: typing.TypeAlias = flatbuffers.number_types.UOffsetTFlags.py_type

class WinType(object):
  RESIGNATION: int
  RATKING_DESTROYED: int
  BACKSTAB_RATKING_DESTROYED: int
  MORE_POINTS: int
  MORE_ROBOTS: int
  MORE_CHEESE: int
  TIE: int
  COIN_FLIP: int

