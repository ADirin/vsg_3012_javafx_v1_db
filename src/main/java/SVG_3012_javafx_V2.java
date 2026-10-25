import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.ResourceBundle;

public class SVG_3012_javafx_V2 extends Application {

    // Change these to match your HeidiSQL / MariaDB connection in Windows
    //private static final String DB_URL = "jdbc:mariadb://localhost:3306/time_calculator";
    // Using the dockerized MariaDB
    private static final String DB_URL ="jdbc:mariadb://host.docker.internal:3306/time_calculator?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Test12";

    private ResourceBundle rs;
    private String currentLang = "en";

    private final Label distanceLabel = new Label();
    private final Label speedLabel = new Label();
    private final Label resultLabel = new Label();
    private final Label messageLabel = new Label();
    private final Label dbLabel = new Label();
    private final TextField distanceField = new TextField();
    private final TextField speedField = new TextField();
    private final Button calcButton = new Button();

    @Override
    public void start(Stage stage) {
        ComboBox<String> langBox = new ComboBox<>();
        langBox.getItems().addAll("Finnish", "Farsi", "English");
        langBox.setValue("English");
        langBox.setOnAction(e -> updateLanguage(langBox.getValue()));

        calcButton.setOnAction(e -> calculate());

        VBox root = new VBox(10,
                new Label("Language:"), langBox,
                distanceLabel, distanceField,
                speedLabel, speedField,
                calcButton, resultLabel, messageLabel, dbLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        updateLanguage("English");

        stage.setTitle("Time Calculator_DB");
        stage.setScene(new Scene(root, 350, 420));
        stage.show();
    }

    private void updateLanguage(String choice) {
        Locale locale;
        switch (choice) {
            case "Finnish":
                locale = new Locale("fi", "FI");
                currentLang = "fi";
                break;
            case "Farsi":
                locale = new Locale("fa", "IR");
                currentLang = "fa";
                break;
            default:
                locale = Locale.getDefault();
                currentLang = "en";
                break;
        }
        rs = ResourceBundle.getBundle("Messages_bundle", locale);
        distanceLabel.setText(rs.getString("distance"));
        speedLabel.setText(rs.getString("speed"));
        calcButton.setText("OK");
        resultLabel.setText("");
        messageLabel.setText("");
        dbLabel.setText("");
    }

    private void calculate() {
        try {
            double distance = Double.parseDouble(distanceField.getText().trim().replace(',', '.'));
            double speed = Double.parseDouble(speedField.getText().trim().replace(',', '.'));
            double time = calTime(distance, speed);
            resultLabel.setText(rs.getString("time") + " " + time);
            messageLabel.setText(rs.getString("message"));

            if (distance >= 0 && speed > 0) {
                saveToDatabase(distance, speed, time, currentLang);
                dbLabel.setText("Saved to database.");
            } else {
                dbLabel.setText("Not saved: distance must be >= 0 and speed > 0.");
            }
        } catch (NumberFormatException ex) {
            resultLabel.setText("Invalid number");
            messageLabel.setText("");
            dbLabel.setText("");
        } catch (SQLException ex) {
            dbLabel.setText("Database error: " + ex.getMessage());
        }
    }

    private static void saveToDatabase(double distance, double speed, double time, String lang)
            throws SQLException {
        String sql = "INSERT INTO calculation (distance, speed, time_hours, language) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, distance);
            ps.setDouble(2, speed);
            ps.setDouble(3, time);
            ps.setString(4, lang);
            ps.executeUpdate();
        }
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