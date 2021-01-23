package com.dubbo.SPI_2.inject;

import com.dubbo.SPI_2.HttpServer;
import org.apache.dubbo.common.URL;

public class TomcatServerInject<T> implements HttpServer {

    private HttpServer httpServer;

    // 注入点
    public void setHttpServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @Override
    public void start(URL url) {
        System.out.println("tomcat2");
        httpServer.start(url);
    }

}
