package battlecode.crossplay;

public class OutOfFuelException extends Exception {
    public OutOfFuelException(String traceback, CrossPlayLanguage language, int budget) {
        super(traceback);
        Language = language;
        FuelBudget = budget;
    }

    public final CrossPlayLanguage Language;
    public final int FuelBudget;
}
