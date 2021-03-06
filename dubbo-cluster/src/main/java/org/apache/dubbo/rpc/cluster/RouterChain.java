/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Router chain
 */
public class RouterChain<T> {

    // full list of addresses from registry, classified by method name.
    // invoker= RegistryDirectory$InvokerDelegate(ProtocolFilterWrapper$匿名内部类(ListenerInvokerWrapper(AsyncToSyncInvoker(DubboInvoker(ExchangeClient)))))
    private List<Invoker<T>> invokers = Collections.emptyList();

    // containing all routers, reconstruct every time 'route://' urls change.
    /**
     * routers:
     *     MockInvokersSelector
     *    TagRouter
     *    ServiceRouter
     *    AppRouter
     */
    private volatile List<Router> routers = Collections.emptyList();

    // Fixed router instances: ConfigConditionRouter, TagRouter, e.g., the rule for each instance may change but the
    // instance will never delete or recreate.
    /**
     * builtinRouters:
     *     MockInvokersSelector
     *    TagRouter
     *    ServiceRouter
     *    AppRouter
     */
    private List<Router> builtinRouters = Collections.emptyList();

    public static <T> RouterChain<T> buildChain(URL url) {
        return new RouterChain<>(url);
    }

    private RouterChain(URL url) {
        /**
         *url= consumer://192.168.1.103/org.apache.dubbo.demo.DemoService?application=demo-consumer
         *      &backup=127.0.0.1:2183,127.0.0.1:2182&check=false&dubbo=2.0.2&enable-auto-migration=true
         *      &enable.auto.migration=true&id=org.apache.dubbo.config.RegistryConfig&init=false
         *      &interface=org.apache.dubbo.demo.DemoService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote
         *      &methods=sayHello,sayHelloAsync&pid=2788&provided-by=demo-provider&qos.port=33333&side=consumer
         *      &sticky=false&timestamp=1612187328433
         */
        /**
         * extensionFactories:
         *     AppRouterFactory
         *     MockRouterFactory
         *     ServiceRouterFactory
         *     TagRouterFactory
         */
        List<RouterFactory> extensionFactories = ExtensionLoader.getExtensionLoader(RouterFactory.class)
                .getActivateExtension(url, "router");

        /**
         * routers:
         *   MockInvokersSelector
         *   TagRouter
         *   ServiceRouter
         *   AppRouter
         */
        List<Router> routers = extensionFactories.stream()
                .map(factory -> factory.getRouter(url))
                .collect(Collectors.toList());

        initWithRouters(routers);
    }

    /**
     * the resident routers must being initialized before address notification.
     * FIXME: this method should not be public
     */
    public void initWithRouters(List<Router> builtinRouters) {
        this.builtinRouters = builtinRouters;
        this.routers = new ArrayList<>(builtinRouters);
        this.sort();
    }

    /**
     * If we use route:// protocol in version before 2.7.0, each URL will generate a Router instance, so we should
     * keep the routers up to date, that is, each time router URLs changes, we should update the routers list, only
     * keep the builtinRouters which are available all the time and the latest notified routers which are generated
     * from URLs.
     *
     * @param routers routers from 'router://' rules in 2.6.x or before.
     */
    public void addRouters(List<Router> routers) {
        List<Router> newRouters = new ArrayList<>();
        newRouters.addAll(builtinRouters);
        newRouters.addAll(routers);
        CollectionUtils.sort(newRouters);
        this.routers = newRouters;
    }

    private void sort() {
        Collections.sort(routers);
    }

    /**
     *
     * @param url
     * @param invocation
     * @return
     */
    public List<Invoker<T>> route(URL url, Invocation invocation) {
        /**
         * 服务目录在刷新 Invoker 列表的过程中，会通过 Router 进行服务路由，筛选出符合路由规则的服务提供者。
         * 服务路由是什么 : 服务路由包含一条路由规则，路由规则决定了服务消费者的调用目标，即规定了服务消费者可调用哪些服务提供者。
         * Dubbo 目前提供的服务路由实现 如下四个
         */
        List<Invoker<T>> finalInvokers = invokers;
        /**
         * routers:
         *     MockInvokersSelector
         *     TagRouter
         *    ServiceRouter
         *    AppRouter
         */
        for (Router router : routers) {
            finalInvokers = router.route(finalInvokers, url, invocation);
        }
        return finalInvokers;
    }

    /**
     * Notify router chain of the initial addresses from registry at the first time.
     * Notify whenever addresses in registry change.
     */
    public void setInvokers(List<Invoker<T>> invokers) {
        // invoker= RegistryDirectory$InvokerDelegate(ProtocolFilterWrapper$匿名内部类(ListenerInvokerWrapper(AsyncToSyncInvoker(DubboInvoker(ExchangeClient)))))
        // 将url转变完成 Invoker 存起来
        this.invokers = (invokers == null ? Collections.emptyList() : invokers);
        routers.forEach(router -> router.notify(this.invokers));
    }
}
