package com.dubbo.SPI_1;

import com.dubbo.SPI_1.api.Car;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.cluster.LoadBalance;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CarDemo {

    public static void main(String[] args) {
        ExtensionLoader<Car> extensionLoader =
                ExtensionLoader.getExtensionLoader(Car.class);
        Car var = extensionLoader.getAdaptiveExtension();
        Map<String, String> map1 = new HashMap<>();
        URL url = new URL("","",1, map1);
        var.getColorForUrl(url);
        Car var1 = extensionLoader.getDefaultExtension();
        Car redCar = extensionLoader.getExtension("black");
        redCar.getColor();


        Car benz = extensionLoader.getExtension("benz");
        Map<String, String> map = new HashMap<>();
        map.put("car", "black");
        URL url1 = new URL("","",1, map);
        url.getMethodParameter("","","");
        benz.getColorForUrl(url1);
        //  Protocol、Cluster、LoadBalance   Transporter 
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
