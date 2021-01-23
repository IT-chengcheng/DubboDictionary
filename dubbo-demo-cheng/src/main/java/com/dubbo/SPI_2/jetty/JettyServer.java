package com.dubbo.SPI_2.jetty;

import com.dubbo.SPI_2.HttpServer;
import org.apache.dubbo.common.URL;

public class JettyServer implements HttpServer {

    @Override
    public void start(URL url) {
        System.out.println("jetty");
    }
}
