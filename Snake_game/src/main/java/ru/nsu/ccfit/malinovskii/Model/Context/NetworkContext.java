package ru.nsu.ccfit.malinovskii.Model.Context;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class NetworkContext {
    private static volatile NetworkContext networkContext;  // Приватная статическая переменная для хранения единственного экземпляра
    private static final String MULTICAST_ADDRESS = "239.192.0.4";
    private static final int MULTICAST_PORT = 9192;
    private final long announcementDelayMS = 1000L;
    private long pingDelayMS = 50L;
    private DatagramSocket socket;
    private long msgSeq = 1;

    private NetworkContext() {

    }


    // Метод для получения единственного экземпляра
    public static NetworkContext getContext() {
        if (networkContext == null) {
            synchronized (NetworkContext.class) {          // Синхронизация для обеспечения потокобезопасности
                if (networkContext == null) {
                    networkContext = new NetworkContext();     // Создание экземпляра, если его нет
                }
            }
        }
        return networkContext;
    }

/*
    public static NetworkContext getContext() {
        if (networkContext == null) {
            throw new IllegalStateException("NetworkContext has not been initialized yet. Use getContext with parameters to initialize.");
        }
        return networkContext;
    }
 */
    public void setPingDelayMS(long delay){
        this.pingDelayMS = delay;
    }

    public static void deleteNetworkContext(){
        networkContext = null;
    }

    public String getMulticastAddress(){
        return MULTICAST_ADDRESS;
    }

    public int getMulticastPort(){
        return MULTICAST_PORT;
    }

    public long getAnnouncementDelayMS(){
        return announcementDelayMS;
    }

    public long getPingDelayMS(){
        return pingDelayMS;
    }

    //Возвращает доступный IPv4 адрес
    public static String getAddress(String networkName) throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();     //Получаем список всех доступных адресов

        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp())        //Не берём лупбэки и неактивные интерфейсы
                continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address) {
                    String ip = addr.getHostAddress();
                    if (iface.getDisplayName().contains(networkName)) return ip;
                }
            }
        }
        return "localhost";
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    public long generateNextMsgSeq() {
        return ++msgSeq;
    }
}
