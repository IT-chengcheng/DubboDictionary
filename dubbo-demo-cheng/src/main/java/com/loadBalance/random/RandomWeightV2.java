package com.loadBalance.random;

import com.loadBalance.ServerIpUtil;


public class RandomWeightV2 {
    public static String getServer() {

        int totalWeight = 0 ;
        for (Integer weight : ServerIpUtil.ServerIpWeights.values()) {
            totalWeight += weight;
        }

        // 随机出坐标轴位置
        int offset = new java.util.Random().nextInt(totalWeight);

        // 去坐标轴寻找
        for (String ip: ServerIpUtil.ServerIpWeights.keySet()) {
            Integer weight = ServerIpUtil.ServerIpWeights.get(ip);

            if (offset <= weight) {
                return ip;
            }

            offset = offset - weight;
        }

        return null;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(getServer());
        }
    }

}
