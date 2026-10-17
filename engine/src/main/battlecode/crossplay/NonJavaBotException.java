package battlecode.crossplay;

public class NonJavaBotException extends Exception {
    static final long serialVersionUID = 0x13a735e6;

    public NonJavaBotException(String traceback) {
        super(traceback);
    }
}
