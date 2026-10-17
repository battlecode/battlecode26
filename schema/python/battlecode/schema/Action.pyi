from __future__ import annotations

import flatbuffers
import numpy as np

import flatbuffers
import typing

uoffset: typing.TypeAlias = flatbuffers.number_types.UOffsetTFlags.py_type

class Action(object):
  NONE: int
  CatFeed: int
  RatAttack: int
  RatNap: int
  RatCollision: int
  PlaceDirt: int
  BreakDirt: int
  CheesePickup: int
  CheeseSpawn: int
  CheeseTransfer: int
  CatScratch: int
  CatPounce: int
  PlaceTrap: int
  RemoveTrap: int
  TriggerTrap: int
  ThrowRat: int
  UpgradeToRatKing: int
  RatSqueak: int
  DamageAction: int
  StunAction: int
  SpawnAction: int
  DieAction: int
  IndicatorStringAction: int
  IndicatorDotAction: int
  IndicatorLineAction: int

