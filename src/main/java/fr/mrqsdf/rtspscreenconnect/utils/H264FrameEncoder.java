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
    private int frameCount = 0;  // compteur de frames

    public H264FrameEncoder() {
        encoder = H264Encoder.createH264Encoder();
    }

    public byte[] encodeFrame(BufferedImage image) {
        // Convertir le BufferedImage en Picture en YUV420
        Picture picture = AWTUtil.fromBufferedImage(image, ColorSpace.YUV420J);

        // Initialisation de la résolution
        if (width == -1 || height == -1) {
            width = picture.getWidth();
            height = picture.getHeight();
        } else if (picture.getWidth() != width || picture.getHeight() != height) {
            width = picture.getWidth();
            height = picture.getHeight();
        }

        // Forcer une I-frame périodiquement (ici toutes les 30 frames)
        int i = Data.fps / 5;
        if (frameCount % i == 0) {
            primed = false;
        }

        // Allouer un ByteBuffer pour recevoir la trame encodée
        ByteBuffer out = ByteBuffer.allocate(picture.getWidth() * picture.getHeight() * 5);

        VideoEncoder.EncodedFrame ef;

        // Si l'encodeur n'est pas encore primé, envoyer une frame dummy pour initialiser les références
        if (!primed) {
            ef = encoder.encodeFrame(picture, out);
            primed = true;
        } else {
            ef = encoder.encodeFrame(picture, out);
        }

        ByteBuffer encodedData = ef.getData();  // Récupère le ByteBuffer contenant les données encodées

        // Extraire les données du ByteBuffer
        byte[] data = new byte[encodedData.remaining()];
        encodedData.get(data);
        frameCount++;
        return data;
    }
}
