package battlecode.crossplay;

public class CrossPlayException extends RuntimeException {
    static final long serialVersionUID = 0x13a735e4;

    public CrossPlayException(String message) {
        super(message + " (If you are a competitor, please report this to the Battlecode staff. This is not an error in your code.)");
    }
}
