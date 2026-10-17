import * as flatbuffers from 'flatbuffers';
/**
 * Triggers the trap
 */
export declare class TriggerTrap {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): TriggerTrap;
    loc(): number;
    team(): number;
    static sizeOf(): number;
    static createTriggerTrap(builder: flatbuffers.Builder, loc: number, team: number): flatbuffers.Offset;
}
