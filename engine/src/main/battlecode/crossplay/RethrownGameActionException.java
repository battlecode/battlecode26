package battlecode.crossplay;

import battlecode.common.GameActionException;
import battlecode.common.GameActionExceptionType;

public class RethrownGameActionException extends GameActionException {
    static final long serialVersionUID = 0x13a735e5;

    public RethrownGameActionException(GameActionExceptionType type, String message, GameActionException cause) {
        super(type, message, cause);
    }

    public RethrownGameActionException(GameActionException cause) {
        super(cause.getType(), cause.getMessage(), cause);
    }
}
