package service;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class LocalizationService {

    private Locale currentLocale = Locale.ROOT;
    private Map<String, String> strings = new HashMap<>();

    public LocalizationService(Locale locale) {
        loadLocale(locale);
    }

    /**
     * Loads the resource bundle for the given locale.
     * Falls back to English, then hardcoded values if bundle is missing.
     */
    public void loadLocale(Locale locale) {
        this.currentLocale = locale;
        strings.clear();

        try {
            ResourceBundle bundle = ResourceBundle.getBundle("Messages_bundle", locale);
            for (String key : bundle.keySet()) {
                strings.put(key, bundle.getString(key));
            }
        } catch (Exception e) {
            System.err.println("Failed to load resource bundle for locale: " + locale);
            // Fallback to English bundle
            try {
                ResourceBundle fallback = ResourceBundle.getBundle("Messages_bundle", Locale.ENGLISH);
                for (String key : fallback.keySet()) {
                    strings.put(key, fallback.getString(key));
                }
            } catch (Exception ex) {
                // Last resort hardcoded defaults
                populateHardcodedDefaults();
            }
        }
    }

    /** Safe key lookup */
    public String get(String key) {
        return strings.getOrDefault(key, "!" + key + "!");
    }

    /** Formats localized string with parameters using MessageFormat */
    public String format(String key, Object... args) {
        String pattern = get(key);
        if (pattern.startsWith("!")) return pattern;
        MessageFormat format = new MessageFormat(pattern, currentLocale);
        return format.format(args);
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    private void populateHardcodedDefaults() {
        strings.put("language", "Language:");
        strings.put("distance", "Distance");
        strings.put("speed", "Speed");
        strings.put("button.calculate", "Calculate");
        strings.put("calc.result", "Travelling {0,number,#.##} {3} at {1,number,#.##} {4} takes {2,number,#.##} {5}.");
        strings.put("message", "Calculation saved to the database.");
        strings.put("error.invalidInput", "Please enter a valid distance (0 or more) and speed (greater than 0).");
        strings.put("db.saved", "Saved to the database.");
        strings.put("db.error", "Database not available. Unit names or history may be missing.");
        strings.put("history.title", "Last calculations:");
        strings.put("history.row", "{0,number,#.##} {1} at {2,number,#.##} {3} = {4,number,#.##} h");
    }
}