package com.loadBalance.hash;

import com.loadBalance.ServerIpUtil;

import java.util.SortedMap;
import java.util.TreeMap;

public class Hash {


    // hash值：服务器ip
    private static SortedMap<Integer, String> vNodes = new TreeMap<>();
    private static Integer vNodeNum = 160;

    static {
        for (String ip : ServerIpUtil.ServerIps) {
            for (int i = 0; i < vNodeNum; i++) {
                vNodes.put(getHash(ip), ip);
            }
        }
    }

    private static String getServer(String client) {
        Integer hash = getHash(client);

        SortedMap<Integer, String> subNodes = vNodes.tailMap(hash);

        Integer firstKey = subNodes.firstKey();

        if (firstKey == null) {
            firstKey = vNodes.firstKey();
        }

        return vNodes.get(firstKey);

    }


    // md5, sha-1, sha-256
    private static int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++)
            hash = (hash ^ str.charAt(i)) * p;
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;

        // 如果算出来的值为负数则取其绝对值
        if (hash < 0)
            hash = Math.abs(hash);
        return hash;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(getServer("client" + i));
        }
    }
}
