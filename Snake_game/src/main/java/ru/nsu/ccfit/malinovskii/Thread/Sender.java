package ru.nsu.ccfit.malinovskii.Thread;

import java.util.concurrent.ConcurrentHashMap;

public class Sender {
    public static final String MULTICAST_ADDRESS = "239.192.0.4";
    public static final int MULTICAST_PORT = 9192;
    private final long announcementDelayMS = 1000L;
    private final long pingDelayMS = 100L;


}
