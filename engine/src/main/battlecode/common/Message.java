package battlecode.common;

public class Message {
    private int senderID;
    private int round;
    private int bytes;
    private MapLocation sourceLoc;

    public Message(int bytes, int senderID, int round, MapLocation sourceLoc) {
        this.senderID = senderID;
        this.round = round;
        this.bytes = bytes;
        this.sourceLoc = sourceLoc;
    }

    public int getSenderID() {
        return senderID;
    }

    public int getRound() {
        return round;
    }

    public int getBytes() {
        return this.bytes;
    }

    public MapLocation getSource() {
        return this.sourceLoc;
    }

    public String toString() {
        return "Message with value " + bytes + " sent from robot with ID " + senderID + " during round " + round + " from location " + sourceLoc + ".";
    }

    public Message copy() {
        return new Message(bytes, senderID, round, sourceLoc);
    }
}
