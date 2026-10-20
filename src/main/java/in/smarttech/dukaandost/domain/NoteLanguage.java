package in.smarttech.dukaandost.domain;

/** Language the owner says the order note is written or spoken in. Unrelated to a customer's reminder language. */
public enum NoteLanguage {
    AUTO("auto-detect"),
    HINDI("Hindi"),
    HINGLISH("Hinglish (Hindi in Roman letters, mixed with English)"),
    ENGLISH("English"),
    MARATHI("Marathi"),
    BENGALI("Bengali"),
    TAMIL("Tamil");

    private final String promptName;

    NoteLanguage(String promptName) {
        this.promptName = promptName;
    }

    public String promptName() {
        return promptName;
    }
}
