package com.dubbo.SPI_1.impl;

import com.dubbo.SPI_1.api.Car;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;

@Activate
public class BenzCar implements Car {

    private Car car;

    // SPI注入的过程：
    // 1. 通过SpiExtensionFactory获取Car的Adaptive类，所以注入进来的对象其实是一个Adaptive类对象，代理对象...
    // 只要满足set  并且length >=4 即可，不需要大写首字母
    public void setkkkk(Car car) {
        this.car = car;
    }

    @Override
    public void getColor() {
        car.getColor();
    }

    @Override
    public void getColorForUrl(URL url) {
        car.getColorForUrl(url);
    }
}
