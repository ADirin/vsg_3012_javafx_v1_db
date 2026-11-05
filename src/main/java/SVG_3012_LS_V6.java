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
import service.LocalizationService;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

public class SVG_3012_LS_V6 extends Application {

    private record Lang(String label, String tag, boolean rtl) {
        String code() { return tag.substring(0, 2); }
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

    private final LocalizationService locService = new LocalizationService(LANGUAGES.get(0).locale());
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

    // ---------------------------------------------------------------- Digits Conversion Logic

    /** Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits and their separators to plain ASCII. */
    static String normalizeDigits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '۰' && c <= '۹') {            // Persian digits
                sb.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {     // Arabic-Indic digits
                sb.append((char) ('0' + (c - '٠')));
            } else if (c == '٫' || c == ',' || c == '،') {   // Arabic decimal sep., comma
                sb.append('.');
            } else if (c == '٬' || Character.isWhitespace(c)) {   // Thousands separator
                // Dropped
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Converts ASCII digits and the decimal point to local digits (Persian or Arabic-Indic). */
    static String toLocalDigits(String s, boolean persian) {
        if (s == null) return "";
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

    /** Public utility to localize any string containing numbers. */
    private String localizeDigits(String s) {
        if (s == null) return "";
        String normalized = normalizeDigits(s);
        return currentLang.usesLocalDigits() ? toLocalDigits(normalized, currentLang.isFarsi()) : normalized;
    }

    private static TextFormatter<String> numericFormatter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String text = change.getText();
            for (char c : text.toCharArray()) {
                boolean ok = (c >= '0' && c <= '9')
                        || (c >= '۰' && c <= '۹')
                        || (c >= '٠' && c <= '٩')
                        || c == '.' || c == ',' || c == '٫' || c == '،' || c == '-';
                if (!ok) {
                    return null;
                }
            }
            return change;
        };
        return new TextFormatter<>(filter);
    }

    private static double parseNumber(String text) {
        return Double.parseDouble(normalizeDigits(text.trim()));
    }

    // ---------------------------------------------------------------- UI Lifecycle

    @Override
    public void start(Stage stage) {
        ComboBox<Lang> langBox = new ComboBox<>();
        langBox.getItems().addAll(LANGUAGES);
        langBox.setValue(LANGUAGES.get(0));
        langBox.setOnAction(e -> updateLanguage(langBox.getValue()));

        distanceField.setTextFormatter(numericFormatter());
        speedField.setTextFormatter(numericFormatter());

        // Automatically convert user input digits on blur / unfocus
        distanceField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) convertFieldDigits(distanceField);
        });
        speedField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) convertFieldDigits(speedField);
        });

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

    private void updateLanguage(Lang lang) {
        currentLang = lang;
        NodeOrientation orientation = lang.rtl()
                ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
        root.setNodeOrientation(orientation);
        distanceField.setNodeOrientation(orientation);
        speedField.setNodeOrientation(orientation);

        // Convert existing values in input fields to selected language digits
        convertFieldDigits(distanceField);
        convertFieldDigits(speedField);

        locService.loadLocale(lang.locale());

        resultLabel.setText("");
        messageLabel.setText("");
        refreshLocalizedTexts();
    }

    private void convertFieldDigits(TextField field) {
        String text = field.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        field.setText(localizeDigits(text));
    }

    private void refreshLocalizedTexts() {
        dbLabel.setText("");
        try {
            units = dao.findAll(currentLang.code());
        } catch (SQLException e) {
            e.printStackTrace();
            units = Map.of();
            dbLabel.setText(locService.get("db.error"));
        }

        languageLabel.setText(locService.get("language"));
        distanceLabel.setText(locService.get("distance") + " (" + unitName("KM") + ")");
        speedLabel.setText(locService.get("speed") + " (" + unitName("KMH") + ")");
        distanceField.setTooltip(tooltipFor("KM"));
        speedField.setTooltip(tooltipFor("KMH"));
        calcButton.setText(locService.get("button.calculate"));
        refreshHistory();
    }

    private String unitName(String code) {
        UnitDao.Unit u = units.get(code);
        return u != null ? u.name() : code;
    }

    private Tooltip tooltipFor(String code) {
        UnitDao.Unit u = units.get(code);
        return (u == null || u.description() == null) ? null : new Tooltip(u.description());
    }

    private void calculate() {
        try {
            // First convert fields to current locale digits before parsing
            convertFieldDigits(distanceField);
            convertFieldDigits(speedField);

            double distance = parseNumber(distanceField.getText());
            double speed = parseNumber(speedField.getText());

            if (speed <= 0 || distance < 0) {
                messageLabel.setText(locService.get("error.invalidInput"));
                return;
            }

            double time = calTime(distance, speed);

            // Format message and ensure numbers render in Persian/Arabic digits
            String formattedResult = locService.format("calc.result",
                    distance, speed, time, unitName("KM"), unitName("KMH"), unitName("H"));

            resultLabel.setText(localizeDigits(formattedResult));
            messageLabel.setText(locService.get("message"));

            // Clear input fields and focus back on distanceField for the next calculation
            distanceField.clear();
            speedField.clear();
            distanceField.requestFocus();

            dbLabel.setText("");
            try {
                dao.saveCalculation(distance, speed, time, currentLang.code(), "KM", "KMH");
                dbLabel.setText(locService.get("db.saved"));
            } catch (SQLException e) {
                e.printStackTrace();
                dbLabel.setText(locService.get("db.error"));
            }
            refreshHistory();

        } catch (NumberFormatException e) {
            messageLabel.setText(locService.get("error.invalidInput"));
        }
    }

    private void refreshHistory() {
        StringBuilder sb = new StringBuilder(locService.get("history.title"));
        try {
            for (UnitDao.HistoryRow r : dao.lastCalculations(currentLang.code(), HISTORY_SIZE)) {
                String rowText = locService.format("history.row",
                        r.distance(), r.distanceUnit(), r.speed(), r.speedUnit(), r.timeHours());
                sb.append('\n').append(localizeDigits(rowText));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        historyLabel.setText(sb.toString());
    }

    public static double calTime(double distance, double speed) {
        if (distance < 0 || speed <= 0) {
            return 0;
        }
        return distance / speed;
    }

    public static void main(String[] args) {
        launch(args);
    }
}