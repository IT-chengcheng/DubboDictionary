package com.dubbo.SPI_2.wrapper;

import com.dubbo.SPI_2.HttpServer;
import org.apache.dubbo.common.URL;

public class TomcatServerWrapper2 implements HttpServer {
    private HttpServer httpServer;

    public TomcatServerWrapper2(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    public void start(URL url) {
        System.out.println("wrapper2");
        httpServer.start(url);
    }
}
