package com.loadBalance.robin;

import com.loadBalance.ServerIpUtil;

public class Robin {

    private static Integer pos = 0;

    public static String getServer() {

        String ip = "";

        synchronized (pos) {
            if (pos.equals(ServerIpUtil.ServerIps.size())) pos = 0;

            ip = ServerIpUtil.ServerIps.get(pos);
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
