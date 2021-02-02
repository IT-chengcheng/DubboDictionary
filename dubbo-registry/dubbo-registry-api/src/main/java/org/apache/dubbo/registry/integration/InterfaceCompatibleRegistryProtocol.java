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
package org.apache.dubbo.registry.integration;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.client.RegistryProtocol;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;

import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.RegistryConstants.ENABLE_REGISTRY_DIRECTORY_AUTO_MIGRATION;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY;
import static org.apache.dubbo.registry.Constants.REGISTER_IP_KEY;

/**
 * RegistryProtocol
 */
public class InterfaceCompatibleRegistryProtocol extends RegistryProtocol {

    @Override
    protected URL getRegistryUrl(Invoker<?> originInvoker) {
        URL registryUrl = originInvoker.getUrl();
        if (REGISTRY_PROTOCOL.equals(registryUrl.getProtocol())) {
            String protocol = registryUrl.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY);
            registryUrl = registryUrl.setProtocol(protocol).removeParameter(REGISTRY_KEY);
        }
        return registryUrl;
    }

    @Override
    protected URL getRegistryUrl(URL url) {
        return URLBuilder.from(url)
                .setProtocol(url.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY))
                .removeParameter(REGISTRY_KEY)
                .build();
    }

    @Override
    protected <T> DynamicDirectory<T> createDirectory(Class<T> type, URL url) {
        /**
         * 1、集群容错源码包含四个部分，分别是服务目录 Directory、服务路由 Router、集群 Cluster 和负载均衡 LoadBalance
         *        这里是集群容错的开始 -> 服务目录 RegistryDirectory
         * 2、服务目录是什么 : 服务目录中存储了一些和服务提供者有关的信息，通过服务目录，服务消费者可获取到服务提供者的信息，
         *       比如 ip、端口、服务协议等。通过这些信息，服务消费者就可通过 Netty 等客户端进行远程调用。在一个服务集群中，
         *       服务提供者数量并不是一成不变的，如果集群中新增了一台机器，相应地在服务目录中就要新增一条服务提供者记录。
         *       或者，如果服务提供者的配置修改了，服务目录中的记录也要做相应的更新。服务目录在获取注册中心的服务配置信息后，
         *       会为每条配置信息生成一个 Invoker 对象，并把这个 Invoker 对象存储起来，这个 Invoker 才是服务目录最终持有的对象。
         *       Invoker 有什么用呢？看名字就知道了，这是一个具有远程调用功能的对象。服务目录可以看做是 Invoker 集合，
         *       且这个集合中的元素会随注册中心的变化而进行动态调整。
         *  3、RegistryDirectory extends DynamicDirectory  implements NotifyListener
         *                               DynamicDirectory  extends AbstractDirectory
         *  4、服务目录目前内置的实现有两个，分别为 StaticDirectory 和 RegistryDirectory，它们均是 AbstractDirectory 的子类。
         *     AbstractDirectory 实现了 Directory 接口，这个接口包含了一个重要的方法定义，即 list(Invocation)，用于列举 Invoker
         * 5、RegistryDirectory 实现了 NotifyListener 接口，当注册中心节点信息发生变化后，
         *     RegistryDirectory 可以通过此接口方法得到变更信息，并根据变更信息动态调整内部 Invoker 列表
         * 6、RegistryDirectory 中有几个比较重要的逻辑，
         *        第一是 Invoker 的列举逻辑
         *        第二是 接收服务配置变更的逻辑
         *        第三是 Invoker 列表的刷新逻辑
         */
        return new RegistryDirectory<>(type, url);
    }

    protected <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        /**
         *  cluster = MockClusterWrapper(FailoverCluster())
         *  registry = ListenerRegistryWrapper(ZookeeperRegistry())
         *  type =  org.apache.dubbo.demo.DemoService
         *  url =  zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-annotation-consumer
         *             &backup=127.0.0.1:2183,127.0.0.1:2182&dubbo=2.0.2&id=org.apache.dubbo.config.RegistryConfig#0&pid=7988&refer=经过encode的一堆值
         */
        // invoker =MockClusterInvoker（AbstractCluster$InterceptorInvokerNode(FailoverClusterInvoker(),ConsumerContextClusterInterceptor())）
        ClusterInvoker<T> invoker = getInvoker(cluster, registry, type, url);
        ClusterInvoker<T> serviceDiscoveryInvoker = getServiceDiscoveryInvoker(cluster, type, url);
        ClusterInvoker<T> migrationInvoker = new MigrationInvoker<>(invoker, serviceDiscoveryInvoker);

        return interceptInvoker(migrationInvoker, url);
    }

    protected <T> ClusterInvoker<T> getServiceDiscoveryInvoker(Cluster cluster, Class<T> type, URL url) {
        Registry registry = registryFactory.getRegistry(super.getRegistryUrl(url));
        ClusterInvoker<T> serviceDiscoveryInvoker = null;
        // enable auto migration from interface address pool to instance address pool
        boolean autoMigration = url.getParameter(ENABLE_REGISTRY_DIRECTORY_AUTO_MIGRATION, false);
        if (autoMigration) {
            DynamicDirectory<T> serviceDiscoveryDirectory = super.createDirectory(type, url);
            serviceDiscoveryDirectory.setRegistry(registry);
            serviceDiscoveryDirectory.setProtocol(protocol);
            Map<String, String> parameters = new HashMap<String, String>(serviceDiscoveryDirectory.getConsumerUrl().getParameters());
            URL urlToRegistry = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
            if (serviceDiscoveryDirectory.isShouldRegister()) {
                serviceDiscoveryDirectory.setRegisteredConsumerUrl(urlToRegistry);
                registry.register(serviceDiscoveryDirectory.getRegisteredConsumerUrl());
            }
            serviceDiscoveryDirectory.buildRouterChain(urlToRegistry);
            serviceDiscoveryDirectory.subscribe(toSubscribeUrl(urlToRegistry));
            serviceDiscoveryInvoker = (ClusterInvoker<T>) cluster.join(serviceDiscoveryDirectory);
        }
        return serviceDiscoveryInvoker;
    }

    private static class MigrationInvoker<T> implements ClusterInvoker<T> {
        private ClusterInvoker<T> invoker;
        private ClusterInvoker<T> serviceDiscoveryInvoker;

        public MigrationInvoker(ClusterInvoker<T> invoker, ClusterInvoker<T> serviceDiscoveryInvoker) {
            this.invoker = invoker;
            this.serviceDiscoveryInvoker = serviceDiscoveryInvoker;
        }

        public ClusterInvoker<T> getInvoker() {
            return invoker;
        }

        public void setInvoker(ClusterInvoker<T> invoker) {
            this.invoker = invoker;
        }

        public ClusterInvoker<T> getServiceDiscoveryInvoker() {
            return serviceDiscoveryInvoker;
        }

        public void setServiceDiscoveryInvoker(ClusterInvoker<T> serviceDiscoveryInvoker) {
            this.serviceDiscoveryInvoker = serviceDiscoveryInvoker;
        }

        @Override
        public Class<T> getInterface() {
            return invoker.getInterface();
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            if (serviceDiscoveryInvoker == null) {
                return invoker.invoke(invocation);
            }

            if (invoker.isDestroyed()) {
                return serviceDiscoveryInvoker.invoke(invocation);
            }
            if (serviceDiscoveryInvoker.isAvailable()) {
                invoker.destroy(); // can be destroyed asynchronously
                return serviceDiscoveryInvoker.invoke(invocation);
            }
            return invoker.invoke(invocation);
        }

        @Override
        public URL getUrl() {
            return invoker.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return invoker.isAvailable() || serviceDiscoveryInvoker.isAvailable();
        }

        @Override
        public void destroy() {
            if (invoker != null) {
                invoker.destroy();
            }
            if (serviceDiscoveryInvoker != null) {
                serviceDiscoveryInvoker.destroy();
            }
        }

        @Override
        public URL getRegistryUrl() {
            return invoker.getRegistryUrl();
        }

        @Override
        public Directory<T> getDirectory() {
            return invoker.getDirectory();
        }

        @Override
        public boolean isDestroyed() {
            return invoker.isDestroyed() && serviceDiscoveryInvoker.isDestroyed();
        }
    }

}
