package fr.mrqsdf.rtspscreenconnect.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class LocalIP {

    public static String getHostAddress(){
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Ignorer les interfaces de boucle locale et celles qui ne sont pas actives
                if (iface.isLoopback() || !iface.isUp())
                    continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Afficher uniquement les adresses IPv4
                    if (addr instanceof Inet4Address) {
                        System.out.println("Interface: " + iface.getDisplayName()
                                + " - Adresse IP: " + addr.getHostAddress());
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        return "localhost";
    }

}
