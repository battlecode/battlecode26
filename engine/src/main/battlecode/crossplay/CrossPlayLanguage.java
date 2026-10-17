package battlecode.crossplay;

public enum CrossPlayLanguage {
    JAVA,
    PYTHON,
    ;

    public static CrossPlayLanguage parse(String languageStr) {
        switch (languageStr.toLowerCase()) {
            case "java":
                return JAVA;
            case "python":
                return PYTHON;
            default:
                throw new IllegalArgumentException("Unsupported cross-play language: " + languageStr);
        }
    }
}
