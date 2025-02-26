package fr.mrqsdf.rtspscreenconnect.rtsp;

import fr.mrqsdf.rtspscreenconnect.resource.Data;
import fr.mrqsdf.rtspscreenconnect.utils.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

public class RTSPServer extends Thread {
    private int rtspPort;
    private int screenId;
    private int rtpPort; // on utilise ce port comme indicateur du port serveur pour RTP
    private String localIP;
    private volatile UnicastRtpSender currentSender; // référence au sender en cours
    private H264FrameEncoder encoder;
    private ScreenCutter screenCutter;
    private String sessionId = null;
    // Indique si la session est en cours (après PLAY)
    private volatile boolean playActive = false;
    private ServerSocket rtspServerSocket;

    public RTSPServer(int rtspPort, int screenId, ScreenCutter cutter) throws Exception {
        this.rtspPort = rtspPort;
        this.screenId = screenId;
        this.rtpPort = rtspPort; // convention : même port pour simplifier
        this.localIP = LocalIP.getHostAddress();
        this.screenCutter = cutter;
        this.encoder = new H264FrameEncoder();
    }

    @Override
    public void interrupt() {
        if (currentSender != null) currentSender.interrupt();
        if (rtspServerSocket != null) {
            try {
                rtspServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.interrupt();
    }

    @Override
    public void run() {
        // Démarrer le thread de capture/encodage pour ce flux.
        new Thread(() -> {
            while (true) {
                try {
                    BufferedImage frame = screenCutter.cutScreen();
                    byte[] encodedFrame = encoder.encodeFrame(frame);
                    if (encodedFrame != null && encodedFrame.length > 0) {
                        // Log de vérification de frame
                        //System.out.println("Screen " + screenId + " : frame encodée, taille = " + encodedFrame.length);
                        // Si la session PLAY est active et qu'un expéditeur est défini, envoyer la frame.
                        if (playActive && currentSender != null) {
                            currentSender.queueFrame(encodedFrame);
                        }
                    } else {
                        System.out.println("Screen " + screenId + " : frame vide");
                    }
                    Thread.sleep(1000/ Data.fps); // environ 30 FPS
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Démarrer le serveur RTSP pour gérer les requêtes des clients.
        try (ServerSocket rtspServerSocket = new ServerSocket(rtspPort)) {
            System.out.println("RTSP Server (unicast) pour l'écran " + screenId + " démarré sur le port " + rtspPort);
            while (!Thread.interrupted()) {
                this.rtspServerSocket = rtspServerSocket;
                if (!rtspServerSocket.isClosed()){
                    Socket clientSocket = rtspServerSocket.accept();
                    System.out.println("Client connecté sur l'écran " + screenId);
                    handleClient(clientSocket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parse tous les en-têtes RTSP de la requête et les retourne dans une Map.
     */
    private Map<String, String> parseHeaders(BufferedReader in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null && line.trim().length() > 0) {
            int colon = line.indexOf(":");
            if (colon != -1) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    private void handleClient(Socket clientSocket) {
        new Thread(() -> {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
            ) {
                // Générer ou réutiliser l'identifiant de session
                String session = (sessionId == null) ? String.valueOf(new Random().nextInt(1000000)) : sessionId;
                sessionId = session;
                boolean exit = false;
                while (!exit) {
                    String requestLine = in.readLine();
                    if (requestLine == null) break;
                    if (requestLine.trim().isEmpty()) continue;
                    System.out.println("[" + screenId + "] " + requestLine);

                    StringTokenizer tokens = new StringTokenizer(requestLine);
                    String method = tokens.nextToken();
                    String url = tokens.nextToken();
                    // Ignorer la version RTSP
                    Map<String, String> headers = parseHeaders(in);
                    String cseq = headers.getOrDefault("CSeq", "1");
                    String transport = headers.get("Transport");

                    if ("OPTIONS".equals(method)) {
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n" +
                                "Public: DESCRIBE, SETUP, PLAY, TEARDOWN, OPTIONS\r\n\r\n";
                        out.write(response);
                        out.flush();
                    } else if ("DESCRIBE".equals(method)) {
                        String sdp = "v=0\r\n" +
                                "o=- 0 0 IN IP4 " + localIP + "\r\n" +
                                "s=Screen" + screenId + "\r\n" +
                                "c=IN IP4 " + localIP + "\r\n" +
                                "t=0 0\r\n" +
                                "a=tool:JavaRTSPServer\r\n" +
                                "m=video " + rtpPort + " RTP/AVP 96\r\n" +
                                "a=rtpmap:96 H264/90000\r\n" +
                                // Paramètres H264 indicatifs (à ajuster si besoin)
                                "a=fmtp:96 packetization-mode=1;profile-level-id=42A01E;sprop-parameter-sets=Z0IAKeKQCgC3,aMljiA==\r\n" +
                                "a=control:trackID=0\r\n";
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n" +
                                "Content-Base: rtsp://" + localIP + ":" + rtspPort + "/" + screenId + "\r\n" +
                                "Content-Type: application/sdp\r\n" +
                                "Content-Length: " + sdp.length() + "\r\n\r\n" +
                                sdp;
                        out.write(response);
                        out.flush();
                    } else if ("SETUP".equals(method)) {
                        // Vérifier si le header Transport contient "client_port"
                        int clientPort = 0;
                        if (transport != null && transport.toLowerCase().contains("client_port=")) {
                            String transportLower = transport.toLowerCase();
                            int idx = transportLower.indexOf("client_port=");
                            if (idx != -1) {
                                int start = idx + "client_port=".length();
                                int dash = transportLower.indexOf("-", start);
                                if (dash != -1) {
                                    String portStr = transportLower.substring(start, dash);
                                    try {
                                        clientPort = Integer.parseInt(portStr);
                                    } catch (NumberFormatException ex) {
                                        clientPort = 0;
                                    }
                                }
                            }
                        }
                        if (clientPort > 0) {
                            String clientIP = clientSocket.getInetAddress().getHostAddress();
                            // Créer l'expéditeur unicast seulement si non défini
                            if (currentSender == null) {
                                currentSender = new UnicastRtpSender(clientIP, clientPort);
                                currentSender.start();
                            }
                            String response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: " + cseq + "\r\n" +
                                    "Session: " + session + "\r\n" +
                                    "Transport: RTP/AVP;unicast;destination=" + localIP +
                                    ";client_port=" + clientPort + ";server_port=" + rtpPort + "\r\n\r\n";
                            out.write(response);
                            out.flush();
                        } else {
                            String response = "RTSP/1.0 454 Session Not Found\r\n" +
                                    "CSeq: " + cseq + "\r\n\r\n";
                            out.write(response);
                            out.flush();
                        }
                    } else if ("PLAY".equals(method)) {
                        playActive = true;
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n" +
                                "Session: " + session + "\r\n\r\n";
                        out.write(response);
                        out.flush();
                    } else if ("TEARDOWN".equals(method)) {
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n" +
                                "Session: " + session + "\r\n\r\n";
                        out.write(response);
                        out.flush();
                        playActive = false;
                        currentSender = null;
                        exit = true;
                    } else {
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n\r\n";
                        out.write(response);
                        out.flush();
                    }
                }
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
