package com.loadBalance.robin;

import com.loadBalance.ServerIpUtil;

import java.util.ArrayList;
import java.util.List;


public class RobinWeight {

    private static Integer pos = 0;



    public static String getServer() {

        List<String> ips = new ArrayList<>();

        for (String ip : ServerIpUtil.ServerIpWeights.keySet()) {
            Integer weight = ServerIpUtil.ServerIpWeights.get(ip);

            for (int i = 0; i < weight; i++) {
                ips.add(ip);
            }
        }

        String ip = "";

        synchronized (pos) {
            if (pos.equals(ips.size())) pos = 0;

            ip = ips.get(pos);
            pos++;
        }

        return ip;

    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(getServer());
        }
    }
}
