package battlecode.util;

import battlecode.common.TrapType;
import battlecode.common.Direction;
import battlecode.common.UnitType;
import battlecode.schema.VecTable;
import battlecode.schema.WinType;
import battlecode.world.DominationFactor;

import com.google.flatbuffers.FlatBufferBuilder;

// import gnu.trove.TByteCollection;
// import gnu.trove.TByteList;
// import gnu.trove.TFloatList;
import gnu.trove.TIntArrayList;
// import gnu.trove.TCharList;
// import gnu.trove.TByteArrayList;


/**
 * Misc. helper functions for working with flatbuffers.
 *
 * @author james
 */
public class FlatHelpers {
    public static byte getSchemaTrapTypeFromTrapType(TrapType type) {
        switch (type) {
            case RAT_TRAP:
                return battlecode.schema.TrapType.RAT_TRAP;
            case CAT_TRAP:
                return battlecode.schema.TrapType.CAT_TRAP;
            default:
                throw new RuntimeException("No schema trap type for " + type);
        }
    }

    public static TrapType getTrapTypeFromSchemaTrapType(byte b) {
        switch (b) {
            case battlecode.schema.TrapType.RAT_TRAP:
                return TrapType.RAT_TRAP;
            case battlecode.schema.TrapType.CAT_TRAP:
                return TrapType.CAT_TRAP;
            default:
                throw new RuntimeException("No trap type for " + b);
        }
    }

    public static UnitType getUnitTypeFromRobotType(byte b) {
        switch (b) {
            case 1:
                return UnitType.BABY_RAT;
            case 2:
                return UnitType.RAT_KING;
            case 3:
                return UnitType.CAT;
            default:
                throw new RuntimeException("No unit type for " + b);
        }
    }

    public static byte getRobotTypeFromUnitType(UnitType type) {
        switch (type) {
            case BABY_RAT:
                return 1;
            case RAT_KING:
                return 2;
            case CAT:
                return 3;
            default:
                throw new RuntimeException("Cannot find byte encoding for " + type);
        }
    }

    public static Direction getDirectionFromOrdinal(int ordinal){
        switch (ordinal){
            case 0:
                return Direction.CENTER;
            case 1:
                return Direction.WEST;
            case 2:
                return Direction.SOUTHWEST;
            case 3:
                return Direction.SOUTH;
            case 4:
                return Direction.SOUTHEAST;
            case 5:
                return Direction.EAST;
            case 6:
                return Direction.NORTHEAST;
            case 7:
                return Direction.NORTH;
            case 8:
                return Direction.NORTHWEST;
            default:
                throw new RuntimeException("Invalid direcitonal ordinal " + ordinal);
        }
    }

    public static int getOrdinalFromDirection(Direction dir){
        switch (dir){
            case CENTER:
                return 0;
            case WEST:
                return 1;
            case SOUTHWEST:
                return 2;
            case SOUTH:
                return 3;
            case SOUTHEAST:
                return 4;
            case EAST:
                return 5;
            case NORTHEAST:
                return 6;
            case NORTH:
                return 7;
            case NORTHWEST:
                return 8;
            default:
                throw new RuntimeException("Invalid diretion " + dir);
        }
    }

    public static byte getWinTypeFromDominationFactor(DominationFactor factor) {
        switch (factor) {
            case KILL_ALL_RAT_KINGS:
                return WinType.RATKING_DESTROYED;
            case MORE_POINTS:
                return WinType.MORE_POINTS;
            case MORE_ROBOTS_ALIVE:
                return WinType.MORE_ROBOTS;
            case MORE_CHEESE:
                return WinType.MORE_CHEESE;
            case WON_BY_DUBIOUS_REASONS:
                return WinType.COIN_FLIP;
            case RESIGNATION:
                return WinType.RESIGNATION;
            default:
                return Byte.MIN_VALUE;
        }
    }

    /**
     * DO NOT CALL THIS WITH OFFSETS!
     * Only call it when you're adding an actual int[] to a buffer,
     * not a Table[].
     * For that, call offsetVector.
     *
     * Well that's a weird API.
     *
     * Call like so:
     * int xyzP = intVector(builder, xyz, BufferType::startXyzVector);
     */
    // public static int intVector(FlatBufferBuilder builder,
    // TIntArrayList arr,
    // ObjIntConsumer<FlatBufferBuilder> start) {
    // final int length = arr.size();
    // start.accept(builder, length);

    // // arrays go backwards in flatbuffers
    // // for reasons
    // for (int i = length - 1; i >= 0; i--) {
    // builder.addInt(arr.get(i));
    // }
    // return builder.endVector();
    // }

    /**
     * This is DIFFERENT from intVector!
     *
     * Call this when you're adding a table of offsets, not flat ints.
     */
    // public static int offsetVector(FlatBufferBuilder builder,
    // TIntArrayList arr,
    // ObjIntConsumer<FlatBufferBuilder> start) {
    // final int length = arr.size();
    // start.accept(builder, length);

    // // arrays go backwards in flatbuffers
    // // for reasons
    // for (int i = length - 1; i >= 0; i--) {
    // builder.addOffset(arr.get(i));
    // }
    // return builder.endVector();
    // }

    // public static int floatVector(FlatBufferBuilder builder,
    // TFloatList arr,
    // ObjIntConsumer<FlatBufferBuilder> start) {
    // final int length = arr.size();
    // start.accept(builder, length);

    // for (int i = length - 1; i >= 0; i--) {
    // builder.addFloat(arr.get(i));
    // }
    // return builder.endVector();
    // }

    // public static int byteVector(FlatBufferBuilder builder,
    // TByteList arr,
    // ObjIntConsumer<FlatBufferBuilder> start) {
    // final int length = arr.size();
    // start.accept(builder, length);

    // for (int i = length - 1; i >= 0; i--) {
    // builder.addByte(arr.get(i));
    // }
    // return builder.endVector();
    // }

    // public static int charVector(FlatBufferBuilder builder,
    // TCharList arr,
    // ObjIntConsumer<FlatBufferBuilder> start) {
    // final int length = arr.size();
    // start.accept(builder, length);

    // for (int i = length - 1; i >= 0; i--) {
    // builder.addInt(arr.get(i));
    // }
    // return builder.endVector();
    // }

    public static int createVecTable(FlatBufferBuilder builder, TIntArrayList xs, TIntArrayList ys) {
        if (xs.size() != ys.size()) {
            throw new RuntimeException("Mismatched x/y length: " + xs.size() + " != " + ys.size());
        }
        // int xsP = intVector(builder, xs, VecTable::startXsVector);
        // int ysP = intVector(builder, ys, VecTable::startYsVector);
        int xsP = VecTable.createXsVector(builder, xs.toNativeArray());
        int ysP = VecTable.createYsVector(builder, ys.toNativeArray());
        return VecTable.createVecTable(builder, xsP, ysP);
    }

    public static int RGBtoInt(int red, int green, int blue) {
        return (red << 16) + (green << 8) + blue;
    }
}
