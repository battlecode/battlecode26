import * as flatbuffers from 'flatbuffers';
import { Turn } from '../../battlecode/schema/turn';
/**
 * A single time-step in a Game, which contains a list of robot turns
 */
export declare class Round {
    bb: flatbuffers.ByteBuffer | null;
    bb_pos: number;
    __init(i: number, bb: flatbuffers.ByteBuffer): Round;
    static getRootAsRound(bb: flatbuffers.ByteBuffer, obj?: Round): Round;
    static getSizePrefixedRootAsRound(bb: flatbuffers.ByteBuffer, obj?: Round): Round;
    /**
     * The IDs of teams in the Game.
     */
    teamIds(index: number): number | null;
    teamIdsLength(): number;
    teamIdsArray(): Int32Array | null;
    /**
     * The total amount of cheese transferred per team
     */
    teamCheeseTransferred(index: number): number | null;
    teamCheeseTransferredLength(): number;
    teamCheeseTransferredArray(): Int32Array | null;
    /**
     * The total number of cat damage done by each team
     */
    teamCatDamage(index: number): number | null;
    teamCatDamageLength(): number;
    teamCatDamageArray(): Int32Array | null;
    /**
     * The total number of alive rat kings per team
     */
    teamAliveRatKings(index: number): number | null;
    teamAliveRatKingsLength(): number;
    teamAliveRatKingsArray(): Int32Array | null;
    /**
     * The total number of alive baby rats per team
     */
    teamAliveBabyRats(index: number): number | null;
    teamAliveBabyRatsLength(): number;
    teamAliveBabyRatsArray(): Int32Array | null;
    /**
     * The total number of live rat traps per team
     */
    teamRatTrapCount(index: number): number | null;
    teamRatTrapCountLength(): number;
    teamRatTrapCountArray(): Int32Array | null;
    /**
     * The total number of live cat traps per team
     */
    teamCatTrapCount(index: number): number | null;
    teamCatTrapCountLength(): number;
    teamCatTrapCountArray(): Int32Array | null;
    /**
     * The total number of live cat traps per team
     */
    teamDirtAmounts(index: number): number | null;
    teamDirtAmountsLength(): number;
    teamDirtAmountsArray(): Int32Array | null;
    /**
     * Ordered turn data for each robot during the round
     */
    turns(index: number, obj?: Turn): Turn | null;
    turnsLength(): number;
    /**
     * The IDs of bodies that died at the end of the round, with no attributable cause.
     */
    diedIds(index: number): number | null;
    diedIdsLength(): number;
    diedIdsArray(): Int32Array | null;
    /**
     * The first sent Round in a match should have index 1. (The starting state,
     * created by the MatchHeader, can be thought to have index 0.)
     * It should increase by one for each following round.
     */
    roundId(): number;
    static startRound(builder: flatbuffers.Builder): void;
    static addTeamIds(builder: flatbuffers.Builder, teamIdsOffset: flatbuffers.Offset): void;
    static createTeamIdsVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamIdsVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamIdsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamCheeseTransferred(builder: flatbuffers.Builder, teamCheeseTransferredOffset: flatbuffers.Offset): void;
    static createTeamCheeseTransferredVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamCheeseTransferredVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamCheeseTransferredVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamCatDamage(builder: flatbuffers.Builder, teamCatDamageOffset: flatbuffers.Offset): void;
    static createTeamCatDamageVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamCatDamageVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamCatDamageVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamAliveRatKings(builder: flatbuffers.Builder, teamAliveRatKingsOffset: flatbuffers.Offset): void;
    static createTeamAliveRatKingsVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamAliveRatKingsVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamAliveRatKingsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamAliveBabyRats(builder: flatbuffers.Builder, teamAliveBabyRatsOffset: flatbuffers.Offset): void;
    static createTeamAliveBabyRatsVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamAliveBabyRatsVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamAliveBabyRatsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamRatTrapCount(builder: flatbuffers.Builder, teamRatTrapCountOffset: flatbuffers.Offset): void;
    static createTeamRatTrapCountVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamRatTrapCountVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamRatTrapCountVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamCatTrapCount(builder: flatbuffers.Builder, teamCatTrapCountOffset: flatbuffers.Offset): void;
    static createTeamCatTrapCountVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamCatTrapCountVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamCatTrapCountVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTeamDirtAmounts(builder: flatbuffers.Builder, teamDirtAmountsOffset: flatbuffers.Offset): void;
    static createTeamDirtAmountsVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createTeamDirtAmountsVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startTeamDirtAmountsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addTurns(builder: flatbuffers.Builder, turnsOffset: flatbuffers.Offset): void;
    static createTurnsVector(builder: flatbuffers.Builder, data: flatbuffers.Offset[]): flatbuffers.Offset;
    static startTurnsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addDiedIds(builder: flatbuffers.Builder, diedIdsOffset: flatbuffers.Offset): void;
    static createDiedIdsVector(builder: flatbuffers.Builder, data: number[] | Int32Array): flatbuffers.Offset;
    /**
     * @deprecated This Uint8Array overload will be removed in the future.
     */
    static createDiedIdsVector(builder: flatbuffers.Builder, data: number[] | Uint8Array): flatbuffers.Offset;
    static startDiedIdsVector(builder: flatbuffers.Builder, numElems: number): void;
    static addRoundId(builder: flatbuffers.Builder, roundId: number): void;
    static endRound(builder: flatbuffers.Builder): flatbuffers.Offset;
    static createRound(builder: flatbuffers.Builder, teamIdsOffset: flatbuffers.Offset, teamCheeseTransferredOffset: flatbuffers.Offset, teamCatDamageOffset: flatbuffers.Offset, teamAliveRatKingsOffset: flatbuffers.Offset, teamAliveBabyRatsOffset: flatbuffers.Offset, teamRatTrapCountOffset: flatbuffers.Offset, teamCatTrapCountOffset: flatbuffers.Offset, teamDirtAmountsOffset: flatbuffers.Offset, turnsOffset: flatbuffers.Offset, diedIdsOffset: flatbuffers.Offset, roundId: number): flatbuffers.Offset;
}
