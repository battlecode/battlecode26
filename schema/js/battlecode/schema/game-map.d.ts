import * as flatbuffers from 'flatbuffers';
import { InitialBodyTable } from '../../battlecode/schema/initial-body-table';
import { Vec } from '../../battlecode/schema/vec';
import { VecTable } from '../../battlecode/schema/vec-table';
export declare class GameMap {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): GameMap;
    static getRootAsGameMap(bb: flatbuffers.ByteBuffer, obj?: GameMap): GameMap;
    static getSizePrefixedRootAsGameMap(bb: flatbuffers.ByteBuffer, obj?: GameMap): GameMap;
    name(): string | null;
    name(optionalEncoding: flatbuffers.Encoding): string | Uint8Array | null;
    size(obj?: Vec): Vec | null;
    symmetry(): number;
    initialBodies(obj?: InitialBodyTable): InitialBodyTable | null;
    randomSeed(): number;
    walls(index: number): boolean | null;
    wallsLength(): number;
    wallsArray(): Int8Array | null;
    dirt(index: number): boolean | null;
    dirtLength(): number;
    dirtArray(): Int8Array | null;
    cheese(index: number): number | null;
    cheeseLength(): number;
    cheeseArray(): Int8Array | null;
    cheeseMines(obj?: VecTable): VecTable | null;
    catWaypointIds(index: number): number | null;
    catWaypointIdsLength(): number;
    catWaypointIdsArray(): Uint16Array | null;
    catWaypointVecs(index: number, obj?: VecTable): VecTable | null;
    catWaypointVecsLength(): number;
    static startGameMap(builder: flatbuffers.Builder): void;
    static addName(builder: flatbuffers.Builder, nameOffset: flatbuffers.Offset): void;
    static addSize(builder: flatbuffers.Builder, sizeOffset: flatbuffers.Offset): void;
    static addSymmetry(builder: flatbuffers.Builder, symmetry: number): void;
    static addInitialBodies(builder: flatbuffers.Builder, initialBodiesOffset: flatbuffers.Offset): void;
    static addRandomSeed(builder: flatbuffers.Builder, randomSeed: number): void;
    static addWalls(builder: flatbuffers.Builder, wallsOffset: flatbuffers.Offset): void;
    static createWallsVector(builder: flatbuffers.Builder, data: boolean[]): flatbuffers.Offset;
    static startWallsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addDirt(builder: flatbuffers.Builder, dirtOffset: flatbuffers.Offset): void;
    static createDirtVector(builder: flatbuffers.Builder, data: boolean[]): flatbuffers.Offset;
    static startDirtVector(builder: flatbuffers.Builder, numElems: number): void;
    static addCheese(builder: flatbuffers.Builder, cheeseOffset: flatbuffers.Offset): void;
    static createCheeseVector(builder: flatbuffers.Builder, data: number[] | Int8Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createCheeseVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startCheeseVector(builder: flatbuffers.Builder, numElems: number): void;
    static addCheeseMines(builder: flatbuffers.Builder, cheeseMinesOffset: flatbuffers.Offset): void;
    static addCatWaypointIds(builder: flatbuffers.Builder, catWaypointIdsOffset: flatbuffers.Offset): void;
    static createCatWaypointIdsVector(builder: flatbuffers.Builder, data: number[] | Uint16Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createCatWaypointIdsVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startCatWaypointIdsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addCatWaypointVecs(builder: flatbuffers.Builder, catWaypointVecsOffset: flatbuffers.Offset): void;
    static createCatWaypointVecsVector(builder: flatbuffers.Builder, data: flatbuffers.Offset[]): flatbuffers.Offset;
    static startCatWaypointVecsVector(builder: flatbuffers.Builder, numElems: number): void;
    static endGameMap(builder: flatbuffers.Builder): flatbuffers.Offset;
}
