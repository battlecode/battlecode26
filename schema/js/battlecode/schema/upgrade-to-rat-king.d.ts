import * as flatbuffers from 'flatbuffers';
/**
 * Upgrade a 3x3 area of rats into a rat king
 */
export declare class UpgradeToRatKing {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): UpgradeToRatKing;
    /**
     * Placeholder data bc the struct cant be empty
     */
    phantom(): number;
    static sizeOf(): number;
    static createUpgradeToRatKing(builder: flatbuffers.Builder, phantom: number): flatbuffers.Offset;
}
