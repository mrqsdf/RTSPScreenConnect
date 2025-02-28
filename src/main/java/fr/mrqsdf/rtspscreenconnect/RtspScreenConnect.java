package fr.mrqsdf.rtspscreenconnect;

import fr.mrqsdf.rtspscreenconnect.resource.Data;
import fr.mrqsdf.rtspscreenconnect.utils.ScreenCollector;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.*;

public class RtspScreenConnect extends Application {

    private static boolean started = false;
    private static RtspScreenConnect instance;

    public static RtspScreenConnect getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) {
        instance = this;
        Data.screens = ScreenCollector.getScreenDevices();
        Data.root = new BorderPane();
        Data.scene = new Scene(Data.root, 640, 360);

        Label label = new Label("Fps : ");
        TextField fpsField = new TextField();
        fpsField.setText("15");
        fpsField.setMaxWidth(50);

        label.setId("fps-label");
        fpsField.setId("fps-field");

        GridPane grid = new GridPane();
        grid.add(label, 0, 0);
        grid.add(fpsField, 1, 0);

        Button button = new Button("Start");
        button.setOnAction(e -> {
            if (started) {
                ScreenStreamer.stop();
                started = false;
                button.setText("Start");
            } else {
                started = true;
                Data.fps = Integer.parseInt(fpsField.getText());
                ScreenStreamer.main(new String[]{});
                button.setText("Stop");
            }
        });
        button.setId("start-stop-button");

        Image image = new Image(getClass().getResource("/images/screen.png").toExternalForm());
        int row = 1;
        for (GraphicsDevice screen : Data.screens) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(100);
            imageView.setFitHeight(100);

            Label screenLabel = new Label("Écran : " + screen.getIDstring() + "    ");
            screenLabel.setId("screen-label");

            // Créer une CheckBox pour sélectionner l'écran
            CheckBox checkBox = new CheckBox("Écran " + (row + 1));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected()) {
                    Data.selectedScreens.add(screen);
                } else {
                    Data.selectedScreens.remove(screen);
                }
            });

            // Ajouter les composants dans la GridPane
            grid.add(screenLabel, 0, row);
            grid.add(checkBox, 1, row);
            grid.add(imageView, 2, row);
            row++;
        }

        Data.data = FXCollections.observableArrayList();

        ListView<String> listView = new ListView<>(Data.data);
        listView.setId("list-view");
        Data.data.addAll(ScreenStreamer.getRtspList());

        Data.root.setRight(listView);
        Data.root.setId("root-pane");



        VBox vbox = new VBox();
        vbox.getChildren().addAll(grid, button);
        vbox.setId("center-box");
        Data.root.setCenter(vbox);

        Data.scene.getStylesheets().add(getClass().getResource("/style/styles.css").toExternalForm());


        stage.setScene(Data.scene);
        stage.setTitle("RTSP Screen Connect");
        stage.show();

        stage.setOnCloseRequest(e -> {
            ScreenStreamer.stop();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
