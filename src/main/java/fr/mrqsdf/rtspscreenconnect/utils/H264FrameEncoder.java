package fr.mrqsdf.rtspscreenconnect.utils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import fr.mrqsdf.rtspscreenconnect.resource.Data;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.VideoEncoder;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.scale.AWTUtil;

public class H264FrameEncoder {
    private H264Encoder encoder;
    private boolean primed = false;
    private int width = -1;
    private int height = -1;
    private int frameCount = 0;
    // Taille du GOP : par exemple, 30 frames
    private int gopSize = Data.fps / 5 / (Data.fps / 15);

    public H264FrameEncoder() {
        encoder = H264Encoder.createH264Encoder();
        encoder.setKeyInterval(gopSize);
    }

    public byte[] encodeFrame(BufferedImage image) {
        // Convertir le BufferedImage en Picture en YUV420
        Picture picture = AWTUtil.fromBufferedImage(image, ColorSpace.YUV420J);

        // Initialiser la résolution si nécessaire
        if (width == -1 || height == -1) {
            width = picture.getWidth();
            height = picture.getHeight();
        } else if (picture.getWidth() != width || picture.getHeight() != height) {
            width = picture.getWidth();
            height = picture.getHeight();
        }

        // Forcer l'envoi d'une I-frame toutes les gopSize frames
        if (frameCount % gopSize == 0) {
            primed = false;
        }

        ByteBuffer out = ByteBuffer.allocate(picture.getWidth() * picture.getHeight() * 5);
        VideoEncoder.EncodedFrame ef;
        if (!primed) {
            // La première frame (ou après réinitialisation) sera une I-frame
            ef = encoder.encodeFrame(picture, out);
            primed = true;
        } else {
            ef = encoder.encodeFrame(picture, out);
        }

        ByteBuffer encodedData = ef.getData();
        byte[] data = new byte[encodedData.remaining()];
        encodedData.get(data);
        frameCount++;
        return data;
    }
}


