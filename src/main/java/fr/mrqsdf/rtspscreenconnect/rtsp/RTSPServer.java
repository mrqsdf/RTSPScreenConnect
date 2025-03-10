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
    private volatile RtspSender currentSender; // référence au sender en cours
    private H264FrameEncoder encoder;
    private ScreenCutter screenCutter;
    private String sessionId = null;
    // Indique si la session est en cours (après PLAY)
    private volatile boolean playActive = false;
    private ServerSocket rtspServerSocket;

    public boolean running = true;

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
        if (currentSender != null) {
            ((Thread) currentSender).interrupt();
            currentSender = null;
        }
        if (rtspServerSocket != null) {
            try {
                rtspServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        running = false;
        super.interrupt();
    }

    @Override
    public void run() {
        // Démarrer le thread de capture/encodage pour ce flux.
        new Thread(() -> {
            while (running) {
                try {
                    if (currentSender != null){
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
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    running = false;
                }
            }
        }).start();

        // Démarrer le serveur RTSP pour gérer les requêtes des clients.
        try (ServerSocket rtspServerSocket = new ServerSocket(rtspPort)) {
            System.out.println("RTSP Server (unicast) pour l'écran " + screenId + " démarré sur le port " + rtspPort);
            while (running) {
                this.rtspServerSocket = rtspServerSocket;
                if (!rtspServerSocket.isClosed()){
                    try {
                        Socket clientSocket = rtspServerSocket != null ? rtspServerSocket.accept() : null;
                        if (clientSocket == null) break;
                        System.out.println("Client connecté sur l'écran " + screenId);
                        handleClient(clientSocket);
                    } catch (SocketException se) {
                        // Socket fermé, on sort de la boucle.
                        System.out.println("ServerSocket fermé, arrêt du serveur RTSP pour l'écran " + screenId);
                        running = false;
                        break;
                    }
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
                    if (clientSocket.isClosed()) {
                        break;
                    }
                    String requestLine = in.readLine();
                    if (requestLine == null) break;
                    if (requestLine.trim().isEmpty()) continue;
                    System.out.println("[" + screenId + "] " + requestLine);

                    StringTokenizer tokens = new StringTokenizer(requestLine);
                    if (!tokens.hasMoreTokens()) {
                        System.out.println("Requête RTSP vide ou mal formée.");
                        continue;
                    }
                    String method = tokens.nextToken();

                    // Ignorer la version RTSP
                    Map<String, String> headers = parseHeaders(in);
                    String cseq = headers.getOrDefault("CSeq", "1");
                    String transport = headers.get("Transport");
                    System.out.println(headers);

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
                                "a=framerate:"+ Data.fps + "\r\n" +
                                "a=fmtp:96 packetization-mode=1;profile-level-id=42A01E;sprop-parameter-sets=Z0IAKeKQCgC3,aMljiA==\r\n" +
                                "a=control:trackID=0\r\n";
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n" +
                                "Content-Base: rtsp://" + localIP + ":" + rtspPort + "/" + screenId + "\r\n" +
                                "Content-Type: application/sdp\r\n" +
                                "Content-Length: " + sdp.length() + "\r\n\r\n" +
                                sdp;
                        System.out.println("Réponse DESCRIBE : " + response);
                        out.write(response);
                        out.flush();
                    } else if ("SETUP".equals(method)) {
                        // Vérifier le header Transport pour choisir TCP ou UDP
                        if (transport != null && transport.toLowerCase().contains("tcp")) {
                            // Mode TCP interleaved
                            int rtpChannel = 0;
                            int idx = transport.toLowerCase().indexOf("interleaved=");
                            if (idx != -1) {
                                int start = idx + "interleaved=".length();
                                int dash = transport.indexOf("-", start);
                                if (dash != -1) {
                                    String channelStr = transport.substring(start, dash);
                                    try {
                                        rtpChannel = Integer.parseInt(channelStr);
                                    } catch (NumberFormatException ex) {
                                        rtpChannel = 0;
                                    }
                                }
                            }
                            System.out.println("RTP Channel : " + rtpChannel);
                            System.out.println("Client IP : " + clientSocket.getInetAddress().getHostAddress());
                            System.out.println("tcp://" + clientSocket.getInetAddress().getHostAddress() + ":" + rtpChannel);
                            TcpRtpSender tcpSender = new TcpRtpSender(clientSocket, rtpChannel);
                            tcpSender.start();
                            currentSender = tcpSender;
                            // Renvoyer le header Transport tel quel (ou adapté) dans la réponse
                            String response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: " + cseq + "\r\n" +
                                    "Session: " + session + "\r\n" +
                                    "Transport: " + transport + "\r\n\r\n";
                            out.write(response);
                            out.flush();
                        } else if (transport != null && transport.toLowerCase().contains("client_port=")) {
                            // Mode UDP (existant)
                            int clientPort = 0;
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
                            if (clientPort > 0) {
                                String clientIP = clientSocket.getInetAddress().getHostAddress();
                                if (currentSender == null) {
                                    currentSender = new UnicastRtpSender(clientIP, clientPort);
                                    ((Thread) currentSender).start();
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
                    } else if ("GET_PARAMETER".equals(method)) {
                        // Simple réponse GET_PARAMETER pour indiquer que la session est active.
                        String response = "RTSP/1.0 200 OK\r\n" +
                                "CSeq: " + cseq + "\r\n" +
                                "Session: " + session + "\r\n\r\n";
                        out.write(response);
                        out.flush();
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
