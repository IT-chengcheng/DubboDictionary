package com.loadBalance.robin;

public class Sequence {

    private static Integer num = 1;

    public static Integer next() {
        return num++;
    }
}
