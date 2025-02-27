package fr.mrqsdf.rtspscreenconnect;

import fr.mrqsdf.rtspscreenconnect.resource.Data;
import fr.mrqsdf.rtspscreenconnect.rtsp.RTSPServer;
import fr.mrqsdf.rtspscreenconnect.utils.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ScreenStreamer {

    private static RTSPServer[] servers;

    private static List<String> rtsps = new ArrayList<>();

    public static void main(String[] args) {
        // Récupérer le premier écran disponible
        GraphicsDevice[] screens = ScreenCollector.getScreenDevices();
        if(screens.length == 0){
            System.out.println("Aucun écran détecté.");
            return;
        }
        String localIP = LocalIP.getHostAddress();
        System.out.println("Adresse IP locale : " + localIP);
        servers = new RTSPServer[screens.length];
        for(int i = 0; i < screens.length; i++){
            int rtspPort = 5004 + 2 * i; // ports séquentiels : 5004, 5006, 5008, ...
            String rtsp = "rtsp://" + localIP + ":" + rtspPort + "/" + i;
            rtsps.add(rtsp);
            System.out.println("Écran " + i + " -> " + rtsp);
            try {
                ScreenCutter cutter = new ScreenCutter(screens[i]);
                RTSPServer server = new RTSPServer(rtspPort, i, cutter);
                servers[i] = server;
                server.start();
            } catch(Exception e){
                e.printStackTrace();
            }
        }
        Data.data.clear();
        Data.data.addAll(rtsps);
    }

    public static void stop() {
        System.out.println("Arrêt des serveurs RTSP");
        if (servers == null) {
            return;
        }
        for (RTSPServer server : servers) {
            if (server != null) {
                server.interrupt();
            }
        }
        rtsps.clear();
        Data.data.clear();

    }

    public static List<String > getRtspList() {
        return rtsps;
    }

}
