import * as flatbuffers from 'flatbuffers';
/**
 * Place a trap at location
 */
export declare class PlaceTrap {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): PlaceTrap;
    loc(): number;
    team(): number;
    isRatTrapType(): boolean;
    static sizeOf(): number;
    static createPlaceTrap(builder: flatbuffers.Builder, loc: number, team: number, isRatTrapType: boolean): flatbuffers.Offset;
}
