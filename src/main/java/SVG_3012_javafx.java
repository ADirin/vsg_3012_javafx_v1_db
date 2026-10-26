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

import java.util.Locale;
import java.util.ResourceBundle;

public class SVG_3012_javafx extends Application {

    private ResourceBundle rs;

    private final Label distanceLabel = new Label();
    private final Label speedLabel = new Label();
    private final Label resultLabel = new Label();
    private final Label messageLabel = new Label();
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

        // Construct the UI layout
        VBox root = new VBox(10,
                new Label("Language:"), langBox,
                distanceLabel, distanceField,
                speedLabel, speedField,
                calcButton, resultLabel, messageLabel);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        updateLanguage("English");

        stage.setTitle("Time Calculator");
        stage.setScene(new Scene(root, 350, 400));
        stage.show();
    }

    private void updateLanguage(String choice) {
        Locale locale;
        switch (choice) {
            case "Finnish":
                locale = new Locale("fi", "FI");
                break;
            case "Farsi":
                locale = new Locale("fa", "IR");
                break;
            default:
                locale = Locale.getDefault();
                break;
        }
        rs = ResourceBundle.getBundle("Messages_bundle", locale);
        distanceLabel.setText(rs.getString("distance"));
        speedLabel.setText(rs.getString("speed"));
        calcButton.setText("OK");
        resultLabel.setText("");
        messageLabel.setText("");
    }

    private void calculate() {
        try {
            double distance = Double.parseDouble(distanceField.getText().trim().replace(',', '.'));
            double speed = Double.parseDouble(speedField.getText().trim().replace(',', '.'));
            double time = calTime(distance, speed);
            resultLabel.setText(rs.getString("time") + " " + time);
            messageLabel.setText(rs.getString("message"));
        } catch (NumberFormatException ex) {
            resultLabel.setText("Invalid number");
            messageLabel.setText("");
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