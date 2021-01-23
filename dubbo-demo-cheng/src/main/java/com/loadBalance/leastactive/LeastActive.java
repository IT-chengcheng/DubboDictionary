package com.loadBalance.leastactive;

import com.loadBalance.ServerIpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class LeastActive {

    public static String getServer() {

        Integer leastActiveNum = null;
        for (Map.Entry<String, Integer> entry: ServerIpUtil.ACTIVITY_LIST.entrySet()) {
            if (leastActiveNum == null || entry.getValue() < leastActiveNum) {
                leastActiveNum = entry.getValue();
            }
        }

        // 找最新调用次数的服务器,可能有相等的
        List<String> leastActiveIps = new ArrayList<>();
        for (Map.Entry<String, Integer> entry: ServerIpUtil.ACTIVITY_LIST.entrySet()) {
            Integer activeNum = entry.getValue();
            String ip = entry.getKey();
            if (activeNum == leastActiveNum) {
                leastActiveIps.add(ip);
            }
        }

        if (leastActiveIps.size() > 1) {
            // 走随机或轮询
        } else {
            return leastActiveIps.get(0);
        }

        return null;

    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(getServer());
        }
    }
}
