package fr.mrqsdf.rtspscreenconnect.rtsp;

public interface RtspSender {

    void queueFrame(byte[] encodedFrame);

}
