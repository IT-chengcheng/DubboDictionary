package com.dubbo.SPI_1.impl;

import com.dubbo.SPI_1.api.Car;
import org.apache.dubbo.common.URL;

public class RedCar implements Car {

    public void getColor() {
        System.out.println("red");
    }

    @Override
    public void getColorForUrl(URL url) {
        System.out.println("red");
    }
}
