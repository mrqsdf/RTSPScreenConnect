package fr.mrqsdf.rtspscreenconnect.rtsp;

import fr.mrqsdf.rtspscreenconnect.resource.Data;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TcpRtpSender extends Thread implements RtspSender {
    private DataOutputStream outStream;
    private int rtpChannel;
    private int sequenceNumber = 0;
    private long timestamp = 0;
    private BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>();
    // Taille maximale du payload TCP pour fragmentation (similaire à UDP)
    private final int MAX_PAYLOAD = 1400;
    private boolean running = true;

    public TcpRtpSender(Socket clientSocket, int rtpChannel) throws Exception {
        // On utilise directement le flux de sortie du clientSocket
        this.outStream = new DataOutputStream(clientSocket.getOutputStream());
        this.rtpChannel = rtpChannel;
    }

    @Override
    public void queueFrame(byte[] encodedFrame) {
        frameQueue.offer(encodedFrame);
    }

    @Override
    public void interrupt() {
        running = false;
        super.interrupt();
    }

    /**
     * Construit un paquet RTP en ajoutant l'en-tête RTP de 12 octets.
     * Le marker est mis à 1 si c'est la fin d'une trame.
     */
    private byte[] createRtpPacket(byte[] payload, boolean marker) {
        byte[] packet = new byte[12 + payload.length];
        packet[0] = (byte) 0x80; // Version 2
        packet[1] = (byte) ((marker ? 0x80 : 0x00) | 96); // Marker + payload type 96
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) (sequenceNumber & 0xFF);
        packet[4] = (byte) (timestamp >> 24);
        packet[5] = (byte) (timestamp >> 16);
        packet[6] = (byte) (timestamp >> 8);
        packet[7] = (byte) (timestamp);
        int ssrc = 12345678;
        packet[8] = (byte) (ssrc >> 24);
        packet[9] = (byte) (ssrc >> 16);
        packet[10] = (byte) (ssrc >> 8);
        packet[11] = (byte) (ssrc);
        System.arraycopy(payload, 0, packet, 12, payload.length);
        return packet;
    }

    /**
     * Envoie un paquet RTP sur le flux TCP en mode interleaved.
     * Format : 0x24, canal (1 octet), longueur (2 octets big endian), puis le paquet RTP.
     */
    private void sendInterleavedPacket(byte[] rtpPacket) throws Exception {
        synchronized(outStream) {
            outStream.writeByte(0x24);          // '$'
            outStream.writeByte(rtpChannel);      // canal
            outStream.writeShort(rtpPacket.length); // longueur du paquet RTP
            outStream.write(rtpPacket);           // données RTP
            outStream.flush();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                byte[] encodedFrame = frameQueue.take();
                int timestampIncrement = 90000 / Data.fps;
                if (encodedFrame.length <= MAX_PAYLOAD) {
                    byte[] rtpPacket = createRtpPacket(encodedFrame, true);
                    sendInterleavedPacket(rtpPacket);
                    sequenceNumber++;
                    timestamp += timestampIncrement;
                } else {
                    // Fragmentation FU-A (supposant une unique NAL unit)
                    int nalHeader = encodedFrame[0] & 0xFF;
                    int nalType = nalHeader & 0x1F;
                    int fuIndicator = (nalHeader & 0xE0) | 28; // 28 = FU-A type
                    int offset = 1;
                    boolean firstFragment = true;
                    while (offset < encodedFrame.length) {
                        int remaining = encodedFrame.length - offset;
                        int payloadSize = Math.min(remaining, MAX_PAYLOAD - 2); // 2 octets réservés pour FU header
                        byte[] fuPayload = new byte[2 + payloadSize];
                        fuPayload[0] = (byte) fuIndicator;
                        int fuHeader = nalType;
                        if (firstFragment) {
                            fuHeader |= 0x80; // Set S bit
                            firstFragment = false;
                        }
                        if (remaining == payloadSize) {
                            fuHeader |= 0x40; // Set E bit pour le dernier fragment
                        }
                        fuPayload[1] = (byte) fuHeader;
                        System.arraycopy(encodedFrame, offset, fuPayload, 2, payloadSize);
                        boolean marker = (remaining == payloadSize);
                        byte[] rtpPacket = createRtpPacket(fuPayload, marker);
                        sendInterleavedPacket(rtpPacket);
                        sequenceNumber++;
                        offset += payloadSize;
                    }
                    timestamp += timestampIncrement;
                }
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
                running = false;
            }
        }
    }
}
