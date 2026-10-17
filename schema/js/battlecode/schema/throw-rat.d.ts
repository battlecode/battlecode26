import * as flatbuffers from 'flatbuffers';
/**
 * Throw a rat
 */
export declare class ThrowRat {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): ThrowRat;
    /**
     * Id of the thrown rat
     */
    id(): number;
    loc(): number;
    static sizeOf(): number;
    static createThrowRat(builder: flatbuffers.Builder, id: number, loc: number): flatbuffers.Offset;
}
