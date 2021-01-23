package com.dubbo.SPI_1.impl;

import com.dubbo.SPI_1.api.Car;
import org.apache.dubbo.common.URL;

public class Car2Wrapper implements Car{

    private Car car;

    public Car2Wrapper(Car car) {
        System.out.println("122");
        this.car = car;
    }

    @Override
    public void getColor() {
        car.getColor();
    }

    @Override
    public void getColorForUrl(URL url) {

    }
}
