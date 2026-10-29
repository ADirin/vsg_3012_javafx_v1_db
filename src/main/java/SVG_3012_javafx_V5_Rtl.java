import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

/**
 * Time Calculator with two localization layers:
 *  - UI labels and messages: ResourceBundle (Messages_bundle_xx.properties, UTF-8)
 *  - Unit names and descriptions: database, translation-table approach (see init.sql and UnitDao)
 *
 * Numeric input accepts Latin (0-9), Persian (۰-۹) and Arabic-Indic (٠-٩) digits.
 * In right-to-left languages the text fields are right-to-left too.
 */
public class SVG_3012_javafx_V5_Rtl extends Application {

    /** Language choice: label shown in the box, locale tag, text direction. */
    private record Lang(String label, String tag, boolean rtl) {
        String code() { return tag.substring(0, 2); }          // value stored in unit_translation.lang
        Locale locale() { return code().equals("en") ? Locale.ROOT : Locale.forLanguageTag(tag); }
        boolean isFarsi() { return code().equals("fa"); }
        boolean isArabic() { return code().equals("ar"); }
        boolean usesLocalDigits() { return isFarsi() || isArabic(); }
        @Override public String toString() { return label; }
    }

    private static final List<Lang> LANGUAGES = List.of(
            new Lang("English", "en", false),
            new Lang("Suomi", "fi-FI", false),
            new Lang("Svenska", "sv-SE", false),
            new Lang("日本語", "ja-JP", false),
            new Lang("العربية", "ar", true),
            new Lang("فارسی", "fa-IR", true));

    private static final int HISTORY_SIZE = 5;

    private final UnitDao dao = UnitDao.fromEnvironment();

    private ResourceBundle rs;
    private Lang currentLang = LANGUAGES.get(0);
    private Map<String, UnitDao.Unit> units = Map.of();

    private VBox root;

    private final Label languageLabel = new Label();
    private final Label distanceLabel = new Label();
    private final Label speedLabel = new Label();
    private final Label resultLabel = new Label();
    private final Label messageLabel = new Label();
    private final Label dbLabel = new Label();
    private final Label historyLabel = new Label();
    private final TextField distanceField = new TextField();
    private final TextField speedField = new TextField();
    private final Button calcButton = new Button();

    // ---------------------------------------------------------------- digits

    /** Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits and their separators to plain ASCII. */
    static String normalizeDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '۰' && c <= '۹') {            // Persian digits
                sb.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {     // Arabic-Indic digits
                sb.append((char) ('0' + (c - '٠')));
            } else if (c == '٫' || c == ',' || c == '،') {   // Arabic decimal sep., comma
                sb.append('.');
            } else if (c == '٬' || Character.isWhitespace(c)) {   // thousands separator, spaces
                // dropped
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts ASCII digits and the decimal point to the digits of the given script:
     * Persian (۰-۹) when persian is true, Arabic-Indic (٠-٩) otherwise. Decimal separator is ٫ in both.
     */
    static String toLocalDigits(String s, boolean persian) {
        char zero = persian ? '۰' : '٠';
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append((char) (zero + (c - '0')));
            } else if (c == '.') {
                sb.append('٫');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Text shown to the user: local digits for Farsi (Persian) and Arabic (Arabic-Indic), else unchanged. */
    private String localizeDigits(String s) {
        return currentLang.usesLocalDigits() ? toLocalDigits(s, currentLang.isFarsi()) : s;
    }

    /** Lets the user type only digits (any script), a decimal separator and a minus sign. */
    private static TextFormatter<String> numericFormatter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String text = change.getText();
            for (char c : text.toCharArray()) {
                boolean ok = (c >= '0' && c <= '9')
                        || (c >= '۰' && c <= '۹')
                        || (c >= '٠' && c <= '٩')
                        || c == '.' || c == ',' || c == '٫' || c == '،' || c == '-';
                if (!ok) {
                    return null;                              // reject the edit
                }
            }
            return change;
        };
        return new TextFormatter<>(filter);
    }

    /** Parses a number typed in any supported digit script. */
    private static double parseNumber(String text) {
        return Double.parseDouble(normalizeDigits(text.trim()));
    }

    // ------------------------------------------------------------------- UI

    @Override
    public void start(Stage stage) {
        ComboBox<Lang> langBox = new ComboBox<>();
        langBox.getItems().addAll(LANGUAGES);
        langBox.setValue(LANGUAGES.get(0));
        langBox.setOnAction(e -> updateLanguage(langBox.getValue()));

        distanceField.setTextFormatter(numericFormatter());
        speedField.setTextFormatter(numericFormatter());

        calcButton.setOnAction(e -> calculate());

        root = new VBox(10,
                languageLabel, langBox,
                distanceLabel, distanceField,
                speedLabel, speedField,
                calcButton, resultLabel, messageLabel, dbLabel, historyLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        updateLanguage(LANGUAGES.get(0));

        stage.setTitle("Time Calculator");
        stage.setScene(new Scene(root, 420, 560));
        stage.show();
    }

    /** Safe lookup: a missing key shows !key! instead of crashing the application. */
    private String msg(String key) {
        return rs.containsKey(key) ? rs.getString(key) : "!" + key + "!";
    }

    private void updateLanguage(Lang lang) {
        currentLang = lang;
        NodeOrientation orientation = lang.rtl()
                ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
        root.setNodeOrientation(orientation);
        // text fields follow the language direction, so Persian/Arabic numbers are typed right-to-left
        distanceField.setNodeOrientation(orientation);
        speedField.setNodeOrientation(orientation);

        // re-write what is already typed into the digit script of the new language
        convertFieldDigits(distanceField);
        convertFieldDigits(speedField);

        rs = ResourceBundle.getBundle("Messages_bundle", lang.locale());

        resultLabel.setText("");
        messageLabel.setText("");
        refreshLocalizedTexts();
    }

    /** Shows existing input in Persian digits for Farsi and in Latin digits for the other languages. */
    private void convertFieldDigits(TextField field) {
        String text = field.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String ascii = normalizeDigits(text);
        field.setText(localizeDigits(ascii));
    }

    /** Reloads unit names from the database and rewrites every label in the current language. */
    private void refreshLocalizedTexts() {
        dbLabel.setText("");
        try {
            units = dao.findAll(currentLang.code());
        } catch (SQLException e) {
            e.printStackTrace();                 // shows the real reason in the IntelliJ console
            units = Map.of();
            dbLabel.setText(msg("db.error"));
        }

        languageLabel.setText(msg("language"));
        distanceLabel.setText(msg("distance") + " (" + unitName("KM") + ")");
        speedLabel.setText(msg("speed") + " (" + unitName("KMH") + ")");
        distanceField.setTooltip(tooltipFor("KM"));
        speedField.setTooltip(tooltipFor("KMH"));
        calcButton.setText(msg("button.calculate"));
        refreshHistory();
    }

    private String unitName(String code) {
        UnitDao.Unit u = units.get(code);
        return u != null ? u.name() : code;      // code shown if the database is unreachable
    }

    private Tooltip tooltipFor(String code) {
        UnitDao.Unit u = units.get(code);
        return (u == null || u.description() == null) ? null : new Tooltip(u.description());
    }

    private void calculate() {
        try {
            double distance = parseNumber(distanceField.getText());
            double speed = parseNumber(speedField.getText());

            if (speed <= 0 || distance < 0) {
                messageLabel.setText(msg("error.invalidInput"));
                return;
            }

            double time = calTime(distance, speed);

            // Localized result: numbers by locale, unit names from the database
            MessageFormat format = new MessageFormat(msg("calc.result"), currentLang.locale());
            resultLabel.setText(localizeDigits(format.format(new Object[]{
                    distance, speed, time, unitName("KM"), unitName("KMH"), unitName("H")})));

            messageLabel.setText(rs.containsKey("message") ? rs.getString("message") : "");

            dbLabel.setText("");
            try {
                // the database always receives plain numbers, whatever digits the user typed
                dao.saveCalculation(distance, speed, time, currentLang.code(), "KM", "KMH");
                dbLabel.setText(msg("db.saved"));
            } catch (SQLException e) {
                e.printStackTrace();
                dbLabel.setText(msg("db.error"));
            }
            refreshHistory();

        } catch (NumberFormatException e) {
            messageLabel.setText(msg("error.invalidInput"));
        }
    }

    private void refreshHistory() {
        StringBuilder sb = new StringBuilder(msg("history.title"));
        try {
            MessageFormat line = new MessageFormat(msg("history.row"), currentLang.locale());
            for (UnitDao.HistoryRow r : dao.lastCalculations(currentLang.code(), HISTORY_SIZE)) {
                sb.append('\n').append(localizeDigits(line.format(new Object[]{
                        r.distance(), r.distanceUnit(), r.speed(), r.speedUnit(), r.timeHours()})));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        historyLabel.setText(sb.toString());
    }

    public static double calTime(double distance, double speed) {
        if (distance < 0 || speed <= 0) {
            return 0; // Avoid negative values or division by zero
        }
        return distance / speed;
    }

    public static void main(String[] args) {
        launch(args);
    }
}