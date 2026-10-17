from __future__ import annotations

import flatbuffers
import numpy as np

import flatbuffers
import typing

uoffset: typing.TypeAlias = flatbuffers.number_types.UOffsetTFlags.py_type

class ThrowRat(object):
  @classmethod
  def SizeOf(cls) -> int: ...

  def Init(self, buf: bytes, pos: int) -> None: ...
  def Id(self) -> int: ...
  def Loc(self) -> int: ...

def CreateThrowRat(builder: flatbuffers.Builder, id: int, loc: int) -> uoffset: ...

