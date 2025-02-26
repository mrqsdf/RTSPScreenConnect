package fr.mrqsdf.rtspscreenconnect.utils;

import java.awt.*;

public class ScreenCollector {

    private static final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

    public static GraphicsDevice[] getScreenDevices() {
        GraphicsDevice[] screenDevices = ge.getScreenDevices();
        System.out.println(screenDevices.length + " écrans détectés.");
        return screenDevices;
    }

}
