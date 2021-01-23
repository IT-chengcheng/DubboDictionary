package com.loadBalance.hash;

import java.util.SortedMap;
import java.util.TreeMap;

public class TreeMapTest {


    public static void main(String[] args) {
        SortedMap<Integer, String> sortedMap = new TreeMap<>();

        sortedMap.put(1, "1");
        sortedMap.put(2, "2");
        sortedMap.put(3, "3");
        sortedMap.put(4, "4");
        sortedMap.put(5, "5");
        sortedMap.put(6, "6");
        sortedMap.put(8, "8");
        sortedMap.put(10, "10");


        System.out.println(sortedMap.firstKey());

        SortedMap<Integer, String> subMap = sortedMap.tailMap(7);
        System.out.println(subMap.firstKey());

    }
}
