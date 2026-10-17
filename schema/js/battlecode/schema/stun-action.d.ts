import * as flatbuffers from 'flatbuffers';
/**
 * Action for when a robot is stunned
 */
export declare class StunAction {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): StunAction;
    id(): number;
    cooldown(): number;
    static sizeOf(): number;
    static createStunAction(builder: flatbuffers.Builder, id: number, cooldown: number): flatbuffers.Offset;
}
