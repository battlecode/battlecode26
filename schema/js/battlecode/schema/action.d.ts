import { BreakDirt } from '../../battlecode/schema/break-dirt';
import { CatFeed } from '../../battlecode/schema/cat-feed';
import { CatPounce } from '../../battlecode/schema/cat-pounce';
import { CatScratch } from '../../battlecode/schema/cat-scratch';
import { CheesePickup } from '../../battlecode/schema/cheese-pickup';
import { CheeseSpawn } from '../../battlecode/schema/cheese-spawn';
import { CheeseTransfer } from '../../battlecode/schema/cheese-transfer';
import { DamageAction } from '../../battlecode/schema/damage-action';
import { DieAction } from '../../battlecode/schema/die-action';
import { IndicatorDotAction } from '../../battlecode/schema/indicator-dot-action';
import { IndicatorLineAction } from '../../battlecode/schema/indicator-line-action';
import { IndicatorStringAction } from '../../battlecode/schema/indicator-string-action';
import { PlaceDirt } from '../../battlecode/schema/place-dirt';
import { PlaceTrap } from '../../battlecode/schema/place-trap';
import { RatAttack } from '../../battlecode/schema/rat-attack';
import { RatCollision } from '../../battlecode/schema/rat-collision';
import { RatNap } from '../../battlecode/schema/rat-nap';
import { RatSqueak } from '../../battlecode/schema/rat-squeak';
import { RemoveTrap } from '../../battlecode/schema/remove-trap';
import { SpawnAction } from '../../battlecode/schema/spawn-action';
import { StunAction } from '../../battlecode/schema/stun-action';
import { ThrowRat } from '../../battlecode/schema/throw-rat';
import { TriggerTrap } from '../../battlecode/schema/trigger-trap';
import { UpgradeToRatKing } from '../../battlecode/schema/upgrade-to-rat-king';
export declare enum Action {
    NONE = 0,
    CatFeed = 1,
    RatAttack = 2,
    RatNap = 3,
    RatCollision = 4,
    PlaceDirt = 5,
    BreakDirt = 6,
    CheesePickup = 7,
    CheeseSpawn = 8,
    CheeseTransfer = 9,
    CatScratch = 10,
    CatPounce = 11,
    PlaceTrap = 12,
    RemoveTrap = 13,
    TriggerTrap = 14,
    ThrowRat = 15,
    UpgradeToRatKing = 16,
    RatSqueak = 17,
    DamageAction = 18,
    StunAction = 19,
    SpawnAction = 20,
    DieAction = 21,
    IndicatorStringAction = 22,
    IndicatorDotAction = 23,
    IndicatorLineAction = 24
}
export declare function unionToAction(type: Action, accessor: (obj: BreakDirt | CatFeed | CatPounce | CatScratch | CheesePickup | CheeseSpawn | CheeseTransfer | DamageAction | DieAction | IndicatorDotAction | IndicatorLineAction | IndicatorStringAction | PlaceDirt | PlaceTrap | RatAttack | RatCollision | RatNap | RatSqueak | RemoveTrap | SpawnAction | StunAction | ThrowRat | TriggerTrap | UpgradeToRatKing) => BreakDirt | CatFeed | CatPounce | CatScratch | CheesePickup | CheeseSpawn | CheeseTransfer | DamageAction | DieAction | IndicatorDotAction | IndicatorLineAction | IndicatorStringAction | PlaceDirt | PlaceTrap | RatAttack | RatCollision | RatNap | RatSqueak | RemoveTrap | SpawnAction | StunAction | ThrowRat | TriggerTrap | UpgradeToRatKing | null): BreakDirt | CatFeed | CatPounce | CatScratch | CheesePickup | CheeseSpawn | CheeseTransfer | DamageAction | DieAction | IndicatorDotAction | IndicatorLineAction | IndicatorStringAction | PlaceDirt | PlaceTrap | RatAttack | RatCollision | RatNap | RatSqueak | RemoveTrap | SpawnAction | StunAction | ThrowRat | TriggerTrap | UpgradeToRatKing | null;
export declare function unionListToAction(type: Action, accessor: (index: number, obj: BreakDirt | CatFeed | CatPounce | CatScratch | CheesePickup | CheeseSpawn | CheeseTransfer | DamageAction | DieAction | IndicatorDotAction | IndicatorLineAction | IndicatorStringAction | PlaceDirt | PlaceTrap | RatAttack | RatCollision | RatNap | RatSqueak | RemoveTrap | SpawnAction | StunAction | ThrowRat | TriggerTrap | UpgradeToRatKing) => BreakDirt | CatFeed | CatPounce | CatScratch | CheesePickup | CheeseSpawn | CheeseTransfer | DamageAction | DieAction | IndicatorDotAction | IndicatorLineAction | IndicatorStringAction | PlaceDirt | PlaceTrap | RatAttack | RatCollision | RatNap | RatSqueak | RemoveTrap | SpawnAction | StunAction | ThrowRat | TriggerTrap | UpgradeToRatKing | null, index: number): BreakDirt | CatFeed | CatPounce | CatScratch | CheesePickup | CheeseSpawn | CheeseTransfer | DamageAction | DieAction | IndicatorDotAction | IndicatorLineAction | IndicatorStringAction | PlaceDirt | PlaceTrap | RatAttack | RatCollision | RatNap | RatSqueak | RemoveTrap | SpawnAction | StunAction | ThrowRat | TriggerTrap | UpgradeToRatKing | null;
