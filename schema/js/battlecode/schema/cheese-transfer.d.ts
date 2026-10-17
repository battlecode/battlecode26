import * as flatbuffers from 'flatbuffers';
/**
 * Transfer a cheese between rats
 */
export declare class CheeseTransfer {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): CheeseTransfer;
    /**
     * Id of the rat which receives the cheese
     */
    id(): number;
    amount(): number;
    static sizeOf(): number;
    static createCheeseTransfer(builder: flatbuffers.Builder, id: number, amount: number): flatbuffers.Offset;
}
