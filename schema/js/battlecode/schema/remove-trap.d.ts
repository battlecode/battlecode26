import * as flatbuffers from 'flatbuffers';
/**
 * Remove a trap at location
 */
export declare class RemoveTrap {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): RemoveTrap;
    loc(): number;
    team(): number;
    static sizeOf(): number;
    static createRemoveTrap(builder: flatbuffers.Builder, loc: number, team: number): flatbuffers.Offset;
}
