package com.loadBalance.random;

import com.loadBalance.ServerIpUtil;

import java.util.ArrayList;
import java.util.List;

public class RandomWeight {


    public static String getServer() {

        List<String> ips = new ArrayList<>();

        for (String ip : ServerIpUtil.ServerIpWeights.keySet()) {
            Integer weight = ServerIpUtil.ServerIpWeights.get(ip);

            for (int i = 0; i < weight; i++) {
                ips.add(ip);
            }
        }

        java.util.Random random = new java.util.Random();
        int index = random.nextInt(ips.size());

        return ips.get(index);

    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(getServer());
        }
    }
}
