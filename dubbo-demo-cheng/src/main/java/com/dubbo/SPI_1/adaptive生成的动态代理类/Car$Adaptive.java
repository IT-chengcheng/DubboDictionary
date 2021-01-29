package com.dubbo.SPI_1.adaptive生成的动态代理类;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class Car$Adaptive implements com.dubbo.SPI_1.api.Car {
    public void getColorForUrl(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("aaa", "red");
        if (extName == null)
            throw new IllegalStateException("Failed to get extension (com.dubbo.SPI_1.api.Car) name from url (" + url.toString() + ") use keys([aaa])");
        com.dubbo.SPI_1.api.Car extension = (com.dubbo.SPI_1.api.Car) ExtensionLoader.getExtensionLoader(com.dubbo.SPI_1.api.Car.class).getExtension(extName);
        extension.getColorForUrl(arg0);
    }

    public void getColor() {
        throw new UnsupportedOperationException("The method public abstract void com.dubbo.SPI_1.api.Car.getColor() of interface com.dubbo.SPI_1.api.Car is not adaptive method!.");
    }
}