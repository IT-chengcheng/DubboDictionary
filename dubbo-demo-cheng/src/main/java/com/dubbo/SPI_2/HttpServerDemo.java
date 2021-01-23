package com.dubbo.SPI_2;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class HttpServerDemo {

    public static void main(String[] args) {
        ExtensionLoader<HttpServer> extensionLoader =
                ExtensionLoader.getExtensionLoader(HttpServer.class);
        HttpServer jettyServer = extensionLoader.getExtension("jetty");
        jettyServer.start(null);

        HttpServer tomcatServer = extensionLoader.getExtension("tomcat");
        tomcatServer.start(null);
    }
}
