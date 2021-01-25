package com.dubbo.SPI_1.impl;

import com.dubbo.SPI_1.api.Car;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

//@Adaptive
public class AdaptiveCar implements Car {

    @Override
    public void getColor() {

    }

    @Override
    public void getColorForUrl(URL url) {
        System.out.println("123123");

    }
}
