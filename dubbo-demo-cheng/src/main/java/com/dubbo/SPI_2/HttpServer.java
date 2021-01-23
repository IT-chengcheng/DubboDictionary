package com.dubbo.SPI_2;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface HttpServer {


    // ExtensionLoader 要注入依赖扩展点时，如何决定要注入依赖扩展点的哪个实现。
    // ExtensionLoader 注入的依赖扩展点是一个 Adaptive 实例（类似代理类），直到扩展点方法执行时才决定调用是一个扩展点实现。
    // Dubbo 使用 URL 对象（包含了Key-Value）传递配置信息。
    // 扩展点方法调用会有URL参数（或是参数有URL成员）
    // 这样依赖的扩展点也可以从URL拿到配置信息，所有的扩展点自己定好配置的Key后，配置信息从URL上从最外层传入。URL在配置传递上即是一条总线。
    // 在 Dubbo 的 ExtensionLoader 的扩展点类对应的 Adaptive 实现是在加载扩展点里动态生成。指定提取的 URL 的 Key 通过 @Adaptive 注解在接口方法上提供。
    // 如果@Adaptive("url.httpserver")，就会从url找url.httpserver这个key所对应的值，
    // 如果@Adaptive，则会默认取接口的简单类名，如果HttpServer，然后把这个类目转换成http.server作为url中的key值

    // Dubbo每个方法加上@Adaptive之后就是一个扩展点，当执行这个方法时可以执行到具体的实现类

//    @Adaptive("url.httpserver")
    @Adaptive
    public void start(URL url);

}
