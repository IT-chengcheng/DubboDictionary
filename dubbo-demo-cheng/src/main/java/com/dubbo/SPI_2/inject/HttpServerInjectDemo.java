package com.dubbo.SPI_2.inject;

import com.dubbo.SPI_2.HttpServer;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;


public class HttpServerInjectDemo {

    public static void main(String[] args) {
        ExtensionLoader<HttpServer> extensionLoader =
                ExtensionLoader.getExtensionLoader(HttpServer.class);
        HttpServer tomcatServer = extensionLoader.getExtension("tomcatInject");
        URL url = new URL("p1", "1.2.3.4", 1010, "path1");
        url = url.addParameters("http.server", "tomcat");
        tomcatServer.start(url);
    }
}
