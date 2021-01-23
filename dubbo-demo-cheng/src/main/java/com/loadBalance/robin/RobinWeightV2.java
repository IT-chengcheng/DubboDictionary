package com.loadBalance.robin;

import com.loadBalance.ServerIpUtil;

import java.util.ArrayList;
import java.util.List;

public class RobinWeightV2 {

    private static Integer pos = 0;



    public static String getServer() {

        int totalWeight = 0;
        boolean sameWeight = true;

        Object[] weights = ServerIpUtil.ServerIpWeights.values().toArray();

        for (int i=0; i< weights.length; i++) {
            Integer weight = (Integer) weights[i];
            totalWeight += weight;

            if (sameWeight && i !=0 &&  weight != weights[i-1]) {
                sameWeight = false;
            }
        }


        if (!sameWeight) {

            // 1,2,3,.....1000,1001,1002
            Integer sequenceNum = Sequence.next();

            // 坐标轴
            Integer offset = sequenceNum % totalWeight;
            offset = offset == 0 ? totalWeight : offset;

            // 去坐标轴寻找
            for (String ip : ServerIpUtil.ServerIpWeights.keySet()) {
                Integer weight = ServerIpUtil.ServerIpWeights.get(ip);

                if (offset <= weight) {
                    return ip;
                }

                offset = offset - weight;
            }
        }

        String ip = "";

        synchronized (pos) {
            if (pos.equals(ServerIpUtil.ServerIpWeights.keySet().toArray().length)) pos = 0;

            ip = (String) ServerIpUtil.ServerIpWeights.keySet().toArray()[pos];
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
