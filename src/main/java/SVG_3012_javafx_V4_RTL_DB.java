import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Time Calculator with two localization layers:
 *  - UI labels and messages: ResourceBundle (Messages_bundle_xx.properties, UTF-8)
 *  - Unit names and descriptions: database, translation-table approach (see init.sql and UnitDao)
 */
public class SVG_3012_javafx_V4_RTL_DB extends Application {

    /** Language choice: label shown in the box, locale tag, text direction. */
    private record Lang(String label, String tag, boolean rtl) {
        String code() { return tag.substring(0, 2); }          // value stored in unit_translation.lang
        Locale locale() { return code().equals("en") ? Locale.ROOT : Locale.forLanguageTag(tag); }
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

    @Override
    public void start(Stage stage) {
        ComboBox<Lang> langBox = new ComboBox<>();
        langBox.getItems().addAll(LANGUAGES);
        langBox.setValue(LANGUAGES.get(0));
        langBox.setOnAction(e -> updateLanguage(langBox.getValue()));

        calcButton.setOnAction(e -> calculate());

        root = new VBox(10,
                languageLabel, langBox,
                distanceLabel, distanceField,
                speedLabel, speedField,
                calcButton, resultLabel, messageLabel, dbLabel, historyLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        // keep numeric input left-to-right even in RTL languages
        distanceField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        speedField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

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
        root.setNodeOrientation(lang.rtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        rs = ResourceBundle.getBundle("Messages_bundle", lang.locale());

        resultLabel.setText("");
        messageLabel.setText("");
        refreshLocalizedTexts();
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
            double distance = Double.parseDouble(distanceField.getText().trim().replace(',', '.'));
            double speed = Double.parseDouble(speedField.getText().trim().replace(',', '.'));

            if (speed <= 0 || distance < 0) {
                messageLabel.setText(msg("error.invalidInput"));
                return;
            }

            double time = calTime(distance, speed);

            // Localized result: numbers by locale, unit names from the database
            MessageFormat format = new MessageFormat(msg("calc.result"), currentLang.locale());
            resultLabel.setText(format.format(new Object[]{
                    distance, speed, time, unitName("KM"), unitName("KMH"), unitName("H")}));

            messageLabel.setText(rs.containsKey("message") ? rs.getString("message") : "");

            dbLabel.setText("");
            try {
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
                sb.append('\n').append(line.format(new Object[]{
                        r.distance(), r.distanceUnit(), r.speed(), r.speedUnit(), r.timeHours()}));
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