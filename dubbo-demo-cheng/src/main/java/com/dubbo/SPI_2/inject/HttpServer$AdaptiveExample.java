package com.dubbo.SPI_2.inject;
import com.dubbo.SPI_2.HttpServer;

import org.apache.dubbo.common.extension.ExtensionLoader;


public class HttpServer$AdaptiveExample implements HttpServer {

    public void start(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("http.server");

        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.dubbo.SPI_2.HttpServer) name from url(" + url.toString() + ") use keys([http.server])");
        HttpServer extension = (HttpServer) ExtensionLoader.getExtensionLoader(HttpServer.class).getExtension(extName);
        extension.start(arg0);
    }
}