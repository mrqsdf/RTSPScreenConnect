package fr.mrqsdf.rtspscreenconnect.rtsp;

import fr.mrqsdf.rtspscreenconnect.resource.Data;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UnicastRtpSender extends Thread {
    private DatagramSocket socket;
    private InetAddress clientAddress;
    private int clientPort;
    private int sequenceNumber = 0;
    private long timestamp = 0;
    private BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>();
    // Taille maximale du payload UDP (en octets). Ajustez selon le MTU (par exemple, 1400)
    private final int MAX_PAYLOAD = 1400;

    private boolean running = true;

    public UnicastRtpSender(String clientAddress, int clientPort) throws Exception {
        socket = new DatagramSocket();
        this.clientAddress = InetAddress.getByName(clientAddress);
        this.clientPort = clientPort;
    }

    public void queueFrame(byte[] encodedFrame) {
        frameQueue.offer(encodedFrame);
    }

    @Override
    public void interrupt() {
        running = false;
        super.interrupt();
    }

    /**
     * Construit un paquet RTP en préfixant le payload avec un en-tête RTP (12 octets).
     * @param payload Le payload à envoyer.
     * @param marker Si vrai, le marker bit est mis à 1.
     * @return Le paquet RTP complet sous forme de tableau d'octets.
     */
    private byte[] createRtpPacket(byte[] payload, boolean marker) {
        byte[] packet = new byte[12 + payload.length];
        // Version 2, pas de padding, pas d'extension, pas de CSRC
        packet[0] = (byte) 0x80;
        // Marker bit et payload type 96 (dynamique)
        packet[1] = (byte) ((marker ? 0x80 : 0x00) | 96);
        // Numéro de séquence
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) (sequenceNumber & 0xFF);
        // Timestamp (4 octets)
        packet[4] = (byte) (timestamp >> 24);
        packet[5] = (byte) (timestamp >> 16);
        packet[6] = (byte) (timestamp >> 8);
        packet[7] = (byte) (timestamp);
        // SSRC (valeur fixe pour cet exemple)
        int ssrc = 12345678;
        packet[8] = (byte) (ssrc >> 24);
        packet[9] = (byte) (ssrc >> 16);
        packet[10] = (byte) (ssrc >> 8);
        packet[11] = (byte) (ssrc);
        // Copier le payload
        System.arraycopy(payload, 0, packet, 12, payload.length);
        return packet;
    }

    @Override
    public void run() {
        while (running) {
            try {
                byte[] encodedFrame = frameQueue.take();
                // Si le paquet est suffisamment petit, l'envoyer directement
                if (encodedFrame.length <= MAX_PAYLOAD) {
                    byte[] rtpPacket = createRtpPacket(encodedFrame, true); // marker true => fin de frame
                    DatagramPacket packet = new DatagramPacket(rtpPacket, rtpPacket.length, clientAddress, clientPort);
                    socket.send(packet);
                    sequenceNumber++;
                    timestamp += 90000 / Data.fps;
                } else {
                    // Fragmenter la trame en plusieurs paquets FU-A.
                    // Supposons que encodedFrame représente une unique NAL unit.
                    // Le premier octet est le header NAL.
                    int nalHeader = encodedFrame[0] & 0xFF;
                    int nalType = nalHeader & 0x1F;
                    // FU indicator : F (bit7) + NRI (bits 6-5) + 28 (FU-A)
                    int fuIndicator = (nalHeader & 0xE0) | 28;
                    // Fragmenter à partir de l'octet 1.
                    int offset = 1;
                    boolean firstFragment = true;
                    while (offset < encodedFrame.length) {
                        int remaining = encodedFrame.length - offset;
                        // On réserve 2 octets pour FU-A header, le reste pour la charge utile.
                        int payloadSize = Math.min(remaining, MAX_PAYLOAD - 2);
                        byte[] fuPayload = new byte[2 + payloadSize];
                        // FU indicator
                        fuPayload[0] = (byte) fuIndicator;
                        // FU header : S, E, R (bit 7, 6, 5) + original nalType (bits 4-0)
                        int fuHeader = nalType;
                        if (firstFragment) {
                            fuHeader |= 0x80; // S=1
                            firstFragment = false;
                        }
                        if (remaining == payloadSize) {
                            fuHeader |= 0x40; // E=1 pour le dernier fragment
                        }
                        fuPayload[1] = (byte) fuHeader;
                        // Copier le fragment de charge utile
                        System.arraycopy(encodedFrame, offset, fuPayload, 2, payloadSize);
                        // Créer le paquet RTP pour ce fragment.
                        // Le marker bit est true uniquement pour le dernier fragment.
                        boolean marker = (remaining == payloadSize);
                        byte[] rtpPacket = createRtpPacket(fuPayload, marker);
                        DatagramPacket packet = new DatagramPacket(rtpPacket, rtpPacket.length, clientAddress, clientPort);
                        socket.send(packet);
                        sequenceNumber++;
                        offset += payloadSize;
                    }
                    timestamp += 90000 / Data.fps;
                }
            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }
    }
}
