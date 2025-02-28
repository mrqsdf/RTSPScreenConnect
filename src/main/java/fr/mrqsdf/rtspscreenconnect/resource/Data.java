package fr.mrqsdf.rtspscreenconnect.resource;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Data {

    public volatile static int fps = 15;
    public volatile static int port = 8554;
    public volatile static Scene scene;
    public volatile static BorderPane root;
    public volatile static ObservableList<String> data;

    public volatile static GraphicsDevice[] screens;
    public volatile static List<GraphicsDevice> selectedScreens = new ArrayList<>();


}
