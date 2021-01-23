package com.loadBalance.random;

import com.loadBalance.ServerIpUtil;

public class Random {

    public static String getServer() {

        java.util.Random random = new java.util.Random();
        int index = random.nextInt(ServerIpUtil.ServerIps.size());

        return ServerIpUtil.ServerIps.get(index);

    }

    public static void main(String[] args) {

        for (int i=0; i<10; i++) {
            System.out.println(getServer());
        }
    }
}
