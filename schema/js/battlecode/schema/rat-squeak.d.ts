import * as flatbuffers from 'flatbuffers';
/**
 * Rat squeak action
 */
export declare class RatSqueak {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): RatSqueak;
    loc(): number;
    static sizeOf(): number;
    static createRatSqueak(builder: flatbuffers.Builder, loc: number): flatbuffers.Offset;
}
