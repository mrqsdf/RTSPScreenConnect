package fr.mrqsdf.rtspscreenconnect.utils;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ScreenCutter {

    private Robot robot;
    private Rectangle screenRect;
    private GraphicsDevice screenDevice;

    public ScreenCutter(GraphicsDevice screenDevice) {
        try {
            this.screenDevice = screenDevice;
            this.screenRect = screenDevice.getDefaultConfiguration().getBounds();
            this.robot = new Robot(screenDevice);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public BufferedImage cutScreen() {
        BufferedImage img = robot.createScreenCapture(screenRect);
        return img;

    }

    public BufferedImage cutScreen(int width, int height) {
        BufferedImage original = robot.createScreenCapture(screenRect);
        // Par exemple, réduire à 640x360 :
        BufferedImage resized = ImageResizer.resize(original, width, height);
        return resized;
    }

}
