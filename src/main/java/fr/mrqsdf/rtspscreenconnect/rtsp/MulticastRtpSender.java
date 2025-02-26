package fr.mrqsdf.rtspscreenconnect.rtsp;

import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MulticastRtpSender extends Thread {
    private DatagramSocket socket;
    private InetAddress multicastGroup;
    private int rtpPort; // port pour RTP (par exemple 5004, 5006, etc.)
    private int sequenceNumber = 0;
    private long timestamp = 0;
    private BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>();

    public MulticastRtpSender(String multicastAddress, int rtpPort) throws Exception {
        this.multicastGroup = InetAddress.getByName(multicastAddress);
        this.rtpPort = rtpPort;
        socket = new DatagramSocket();
    }

    public void queueFrame(byte[] encodedFrame) {
        frameQueue.offer(encodedFrame);
    }

    private byte[] createRtpPacket(byte[] payload) {
        byte[] packet = new byte[12 + payload.length];
        packet[0] = (byte) 0x80;
        // Mettre le marker bit (0x80) avec le type de payload (96)
        packet[1] = (byte) (0x80 | 96);
        // Numéro de séquence
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) (sequenceNumber & 0xFF);
        // Timestamp (4 octets)
        packet[4] = (byte) (timestamp >> 24);
        packet[5] = (byte) (timestamp >> 16);
        packet[6] = (byte) (timestamp >> 8);
        packet[7] = (byte) (timestamp);
        // SSRC (4 octets) – valeur fixe pour cet exemple
        int ssrc = 12345678;
        packet[8]  = (byte) (ssrc >> 24);
        packet[9]  = (byte) (ssrc >> 16);
        packet[10] = (byte) (ssrc >> 8);
        packet[11] = (byte) (ssrc);
        System.arraycopy(payload, 0, packet, 12, payload.length);
        return packet;
    }

    @Override
    public void run() {
        while(true){
            try {
                byte[] encodedFrame = frameQueue.take();
                // Pour simplifier, on envoie l'image encodée en un seul paquet RTP
                byte[] rtpPacket = createRtpPacket(encodedFrame);
                DatagramPacket packet = new DatagramPacket(rtpPacket, rtpPacket.length, multicastGroup, rtpPort);
                socket.send(packet);
                sequenceNumber++;
                // Pour 30 FPS et une horloge 90kHz, incrément de 3000
                timestamp += 3000;
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}

