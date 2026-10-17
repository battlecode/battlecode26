package battlecode.crossplay;

public enum CrossPlayLanguage {
    JAVA("Java", 1),
    PYTHON("Python", 0.2f),
    CSHARP("C#", 1),
    ;

    public final String DisplayName;
    public final float FuelCostMultiplier;

    CrossPlayLanguage(String name, float fuelCostMultiplier) {
        DisplayName = name;
        FuelCostMultiplier = fuelCostMultiplier;
    }

    public static CrossPlayLanguage parse(String languageStr) {
        for (CrossPlayLanguage c: CrossPlayLanguage.values())
        {
            if(c.DisplayName.equalsIgnoreCase(languageStr) || c.name().equalsIgnoreCase(languageStr))
                return c;
        }

        throw new IllegalArgumentException("Unsupported cross-play language: " + languageStr);
    }
}
