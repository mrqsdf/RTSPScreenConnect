package fr.mrqsdf.rtspscreenconnect;

import fr.mrqsdf.rtspscreenconnect.resource.Data;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class RtspScreenConnect extends Application {

    private static boolean started = false;

    @Override
    public void start(Stage stage) {
        Data.root = new BorderPane();
        Data.scene = new Scene(Data.root, 640, 360);

        Label label = new Label("Fps : ");
        TextField fpsField = new TextField();
        fpsField.setText("15");
        fpsField.setMaxWidth(50);

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

        Data.data = FXCollections.observableArrayList();

        ListView<String> listView = new ListView<>(Data.data);
        Data.data.addAll(ScreenStreamer.getRtspList());

        Data.root.setRight(listView);



        VBox vbox = new VBox();
        vbox.getChildren().addAll(grid, button);
        Data.root.setCenter(vbox);

        stage.setScene(Data.scene);
        stage.setTitle("RTSP Screen Connect");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
