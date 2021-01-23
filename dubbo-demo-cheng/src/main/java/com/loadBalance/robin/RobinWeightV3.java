package com.loadBalance.robin;

import com.loadBalance.ServerIpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RobinWeightV3 {

    public static List<Weight> weights = new ArrayList<>();
    public static int totalWeight = 0;

    public static String getServer() {
        if (weights.isEmpty()) {
            for (Map.Entry<String, Integer> entry : ServerIpUtil.ServerIpWeights.entrySet()) {
                String ip = entry.getKey();
                Integer weight = entry.getValue();
                totalWeight += weight;
                weights.add(new Weight(weight, weight, ip));
            }
        }

        Weight maxCurrentWeight = null;
        for (Weight weight: weights) {
            if (maxCurrentWeight == null || weight.getCurrentWeight() > maxCurrentWeight.getCurrentWeight()) {
                maxCurrentWeight = weight;
            }
        }


        maxCurrentWeight.setCurrentWeight(maxCurrentWeight.getCurrentWeight() - totalWeight);

        for (Weight weight: weights) {
            weight.setCurrentWeight(weight.getCurrentWeight() + weight.getWeight());
        }


        return maxCurrentWeight.getIp();

    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(getServer());
        }
    }
}
