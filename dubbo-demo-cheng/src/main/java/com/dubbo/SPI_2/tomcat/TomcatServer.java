package com.dubbo.SPI_2.tomcat;

import com.dubbo.SPI_2.HttpServer;
import org.apache.dubbo.common.URL;

public class TomcatServer<T> implements HttpServer {
    @Override
    public void start(URL url) {
        System.out.println("tomcat");
    }

}
