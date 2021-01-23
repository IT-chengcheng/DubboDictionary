package com.loadBalance;

import java.util.*;

public class ServerIpUtil {

    public static List<String> ServerIps = new ArrayList<>();
    static {
        ServerIps.add("192.168.1.1");
        ServerIps.add("192.168.1.2");
        ServerIps.add("192.168.1.3");
        ServerIps.add("192.168.1.4");
        ServerIps.add("192.168.1.5");
        ServerIps.add("192.168.1.6");
        ServerIps.add("192.168.1.7");
        ServerIps.add("192.168.1.8");
        ServerIps.add("192.168.1.9");
        ServerIps.add("192.168.1.10");
    }


    public static Map<String, Integer> ServerIpWeights = new LinkedHashMap<>();

    static {
//        ServerIpWeights.put("192.168.1.1", 5);
//        ServerIpWeights.put("192.168.1.2", 3);
//        ServerIpWeights.put("192.168.1.3", 2);

        ServerIpWeights.put("A", 5);
        ServerIpWeights.put("B", 1);
        ServerIpWeights.put("C", 1);

//        ServerIpWeights.put("192.168.1.4", 7);
//        ServerIpWeights.put("192.168.1.5", 2);
//        ServerIpWeights.put("192.168.1.6", 8);
//        ServerIpWeights.put("192.168.1.7", 1);
//        ServerIpWeights.put("192.168.1.8", 9);
//        ServerIpWeights.put("192.168.1.9", 2);
//        ServerIpWeights.put("192.168.1.10", 8);
    }


    public static final Map<String, Integer> ACTIVITY_LIST = new LinkedHashMap<String, Integer>();
    static {
        ACTIVITY_LIST.put("192.168.0.1", 2);
        ACTIVITY_LIST.put("192.168.0.2", 0);
        ACTIVITY_LIST.put("192.168.0.3", 1);
        ACTIVITY_LIST.put("192.168.0.4", 3);
        ACTIVITY_LIST.put("192.168.0.5", 0);
        ACTIVITY_LIST.put("192.168.0.6", 1);
        ACTIVITY_LIST.put("192.168.0.7", 4);
        ACTIVITY_LIST.put("192.168.0.8", 2);
        ACTIVITY_LIST.put("192.168.0.9", 7);
        ACTIVITY_LIST.put("192.168.0.10", 3);
    }
}
