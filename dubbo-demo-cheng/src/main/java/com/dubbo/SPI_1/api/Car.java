package com.dubbo.SPI_1.api;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface Car {

    public void getColor();

    @Adaptive
    public void getColorForUrl(URL url);
}
