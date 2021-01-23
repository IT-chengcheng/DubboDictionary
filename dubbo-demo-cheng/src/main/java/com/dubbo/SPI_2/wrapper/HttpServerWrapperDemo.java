package com.dubbo.SPI_2.wrapper;

import com.dubbo.SPI_2.HttpServer;
import org.apache.dubbo.common.extension.ExtensionLoader;

public class HttpServerWrapperDemo {

    // 有多个wrapper类的执行顺序是？按配置文件的顺序来
    public static void main(String[] args) {
        ExtensionLoader<HttpServer> extensionLoader = ExtensionLoader.getExtensionLoader(HttpServer.class);
        HttpServer httpServer = extensionLoader.getExtension("tomcat");

        httpServer.start(null);
    }
}
