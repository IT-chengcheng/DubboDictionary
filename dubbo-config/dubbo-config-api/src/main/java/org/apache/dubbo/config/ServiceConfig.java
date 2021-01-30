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
package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.event.ServiceConfigExportedEvent;
import org.apache.dubbo.config.event.ServiceConfigUnexportedEvent;
import org.apache.dubbo.config.invoker.DelegateProviderMetaDataInvoker;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.apache.dubbo.registry.client.metadata.MetadataUtils;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.ConfiguratorFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_IP_TO_BIND;
import static org.apache.dubbo.common.constants.CommonConstants.LOCALHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.MAPPING_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METADATA_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.REGISTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.CommonConstants.REVISION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.utils.NetUtils.getAvailablePort;
import static org.apache.dubbo.common.utils.NetUtils.getLocalHost;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidLocalHost;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidPort;
import static org.apache.dubbo.config.Constants.DUBBO_IP_TO_REGISTRY;
import static org.apache.dubbo.config.Constants.DUBBO_PORT_TO_BIND;
import static org.apache.dubbo.config.Constants.DUBBO_PORT_TO_REGISTRY;
import static org.apache.dubbo.config.Constants.MULTICAST;
import static org.apache.dubbo.config.Constants.SCOPE_NONE;
import static org.apache.dubbo.remoting.Constants.BIND_IP_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_PORT_KEY;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.PROXY_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_LOCAL;
import static org.apache.dubbo.rpc.Constants.SCOPE_REMOTE;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;

public class ServiceConfig<T> extends ServiceConfigBase<T> {

    public static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);

    /**
     * A random port cache, the different protocols who has no port specified have different random port
     */
    private static final Map<String, Integer> RANDOM_PORT_MAP = new HashMap<String, Integer>();

    /**
     * A delayed exposure service timer
     */
    private static final ScheduledExecutorService DELAY_EXPORT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DubboServiceDelayExporter", true));

    private static final Protocol PROTOCOL = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    /**
     * A {@link ProxyFactory} implementation that will generate a exported service proxy,the JavassistProxyFactory is its
     * default implementation
     */
    private static final ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    /**
     * Whether the provider has been exported
     */
    private transient volatile boolean exported;

    /**
     * The flag whether a service has unexported ,if the method unexported is invoked, the value is true
     */
    private transient volatile boolean unexported;

    private DubboBootstrap bootstrap;

    /**
     * The exported services, 盛放所有导出的服务
     */
    private final List<Exporter<?>> exporters = new ArrayList<Exporter<?>>();

    public ServiceConfig() {
    }

    public ServiceConfig(Service service) {
        super(service);
    }

    @Parameter(excluded = true)
    public boolean isExported() {
        return exported;
    }

    @Parameter(excluded = true)
    public boolean isUnexported() {
        return unexported;
    }

    public void unexport() {
        if (!exported) {
            return;
        }
        if (unexported) {
            return;
        }
        if (!exporters.isEmpty()) {
            for (Exporter<?> exporter : exporters) {
                try {
                    exporter.unexport();
                } catch (Throwable t) {
                    logger.warn("Unexpected error occured when unexport " + exporter, t);
                }
            }
            exporters.clear();
        }
        unexported = true;

        // dispatch a ServiceConfigUnExportedEvent since 2.7.4
        dispatch(new ServiceConfigUnexportedEvent(this));
    }

    public synchronized void export() {
        if (bootstrap == null) {
            bootstrap = DubboBootstrap.getInstance();
            bootstrap.initialize();
        }

        checkAndUpdateSubConfigs();

        //init serviceMetadata
        serviceMetadata.setVersion(getVersion());
        serviceMetadata.setGroup(getGroup());
        serviceMetadata.setDefaultGroup(getGroup());
        serviceMetadata.setServiceType(getInterfaceClass());
        serviceMetadata.setServiceInterfaceName(getInterface());
        serviceMetadata.setTarget(getRef());

        if (!shouldExport()) {
            return;
        }

        if (shouldDelay()) {
            DELAY_EXPORT_EXECUTOR.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            doExport();
        }
        // 经过上面的 doExport（），单个服务已经导出完毕，下面触发单个服务导出完毕事件
        exported();
    }

    public void exported() {
        List<URL> exportedURLs = this.getExportedUrls();
        exportedURLs.forEach(url -> {
            Map<String, String> parameters = getApplication().getParameters();
            ServiceNameMapping.getExtension(parameters != null ? parameters.get(MAPPING_KEY) : null).map(url);
        });
        // dispatch a ServiceConfigExportedEvent since 2.7.4
        dispatch(new ServiceConfigExportedEvent(this));
    }

    private void checkAndUpdateSubConfigs() {
        // Use default configs defined explicitly with global scope
        completeCompoundConfigs();
        checkDefault();
        checkProtocol();
        // init some null configuration.
        List<ConfigInitializer> configInitializers = ExtensionLoader.getExtensionLoader(ConfigInitializer.class)
                .getActivateExtension(URL.valueOf("configInitializer://"), (String[]) null);
        configInitializers.forEach(e -> e.initServiceConfig(this));

        // if protocol is not injvm checkRegistry
        if (!isOnlyInJvm()) {
            checkRegistry();
        }
        this.refresh();

        if (StringUtils.isEmpty(interfaceName)) {
            throw new IllegalStateException("<dubbo:service interface=\"\" /> interface not allow null!");
        }
        // 检测 ref 是否为泛化服务类型
        if (ref instanceof GenericService) {
            interfaceClass = GenericService.class;
            if (StringUtils.isEmpty(generic)) {
                generic = Boolean.TRUE.toString();
            }
        } else {
            try {
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            // 对 interfaceClass，以及 <dubbo:method> 标签中的必要字段进行检查
            checkInterfaceAndMethods(interfaceClass, getMethods());
            checkRef();
            generic = Boolean.FALSE.toString();
        }
        // local 和 stub 在功能应该是一致的，用于配置本地存根
        if (local != null) {
            if ("true".equals(local)) { // 如果配置的事true，那么默认类名就是 接口名拼接Local
                local = interfaceName + "Local";
            }
            Class<?> localClass;
            try {
                // 获取本地存根类
                localClass = ClassUtils.forNameWithThreadContextClassLoader(local);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!interfaceClass.isAssignableFrom(localClass)) {
                throw new IllegalStateException("The local implementation class " + localClass.getName() + " not implement interface " + interfaceName);
            }
        }
        // 逻辑同上
        if (stub != null) {
            if ("true".equals(stub)) {
                stub = interfaceName + "Stub";
            }
            Class<?> stubClass;
            try {
                stubClass = ClassUtils.forNameWithThreadContextClassLoader(stub);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!interfaceClass.isAssignableFrom(stubClass)) {
                throw new IllegalStateException("The stub implementation class " + stubClass.getName() + " not implement interface " + interfaceName);
            }
        }
        checkStubAndLocal(interfaceClass);
        ConfigValidationUtils.checkMock(interfaceClass, this);
        ConfigValidationUtils.validateServiceConfig(this);
        postProcessConfig();
    }


    protected synchronized void doExport() {
        if (unexported) {
            throw new IllegalStateException("The service " + interfaceClass.getName() + " has already unexported!");
        }
        if (exported) {
            return;
        }
        exported = true;

        if (StringUtils.isEmpty(path)) {
            path = interfaceName;
        }
        doExportUrls();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doExportUrls() {
        // 需要注册服务的一个本地仓库，也就是本地存储一份服务
        ServiceRepository repository = ApplicationModel.getServiceRepository();
        // 见名知意，就是一个服务描述符，里面有： 接口的名，方法数组，以及参数等等
        ServiceDescriptor serviceDescriptor = repository.registerService(getInterfaceClass());
        repository.registerProvider(
                getUniqueServiceName(),// group/接口名：版本号  -> "greeting/org.apache.dubbo.demo.GreetingService:1.0.0"
                ref, // 接口实现类 GreetingServiceImpl
                serviceDescriptor,
                this, // ServiceBean
                serviceMetadata
        );
        // 加载注册中心链接
        /**
         * 单个 URL -> registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider
         *              &dubbo=2.0.2&id=registry1&mapping-type=metadata&mapping.type=metadata&pid=10000&qos.port=22222
         *              &registry=zookeeper&timestamp=1611892414279
         * 由此分析呢：协议的路径 就是执行动作的类
         */
        List<URL> registryURLs = ConfigValidationUtils.loadRegistries(this, true);

        // 遍历 protocols，并在每个协议下导出服务
        // ProtocolConfig里面就是单个通信协议的各种属性，比如 dubbo，http等
        for (ProtocolConfig protocolConfig : protocols) {
            //  pathKey -> greeting/org.apache.dubbo.demo.GreetingService:1.0.0
            String pathKey = URL.buildKey(getContextPath(protocolConfig)
                    .map(p -> p + "/" + path) // path  -> org.apache.dubbo.demo.GreetingService
                    .orElse(path), group, version);
            // In case user specified path, register service one more time to map it to path.
            // interfaceClass 就是要暴露的接口  -> interface  org.apache.dubbo.demo.GreetingService
            repository.registerService(pathKey, interfaceClass);
            // TODO, uncomment this line once service key is unified   /ˈjuːnɪfaɪd/ 统一的；一致标准的
            serviceMetadata.setServiceKey(pathKey);
            // 根据配置，以及其他一些信息组装 URL。URL 是 Dubbo 配置的载体，通过 URL 可让 Dubbo 的各种配置在各个模块之间传递
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }

    private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {

        String name = protocolConfig.getName();
        if (StringUtils.isEmpty(name)) {
            // 如果协议名为空，或空串，则将协议名变量设置为 dubbo
            name = DUBBO;
        }

        Map<String, String> map = new HashMap<String, String>();
        // 添加 side、版本、时间戳以及进程号等信息到 map 中
        map.put(SIDE_KEY, PROVIDER_SIDE);

        ServiceConfig.appendRuntimeParameters(map);
        AbstractConfig.appendParameters(map, getMetrics());
        // 通过反射将对象的字段信息添加到 map 中
        AbstractConfig.appendParameters(map, getApplication());
        AbstractConfig.appendParameters(map, getModule());
        // remove 'default.' prefix for configs from ProviderConfig
        // appendParameters(map, provider, Constants.DEFAULT_KEY);
        AbstractConfig.appendParameters(map, provider);
        AbstractConfig.appendParameters(map, protocolConfig);
        AbstractConfig.appendParameters(map, this);
        MetadataReportConfig metadataReportConfig = getMetadataReportConfig();
        if (metadataReportConfig != null && metadataReportConfig.isValid()) {
            map.putIfAbsent(METADATA_KEY, REMOTE_METADATA_STORAGE_TYPE);
        }
        if (CollectionUtils.isNotEmpty(getMethods())) {
            // methods 为 MethodConfig 集合，MethodConfig 中存储了 <dubbo:method> 标签的配置信息
            for (MethodConfig method : getMethods()) {
                // 添加 MethodConfig 对象的字段信息到 map 中，键 = 方法名.属性名。
                // 比如存储 <dubbo:method name="sayHello" retries="2"> 对应的 MethodConfig，
                // 键 = sayHello.retries，map = {"sayHello.retries": 2, "xxx": "yyy"}
                AbstractConfig.appendParameters(map, method, method.getName());
                String retryKey = method.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    // 检测 MethodConfig retry 是否为 false，若是，则设置重试次数为0
                    if ("false".equals(retryValue)) {
                        map.put(method.getName() + ".retries", "0");
                    }
                }
                // 获取 ArgumentConfig 列表
                List<ArgumentConfig> arguments = method.getArguments();
                if (CollectionUtils.isNotEmpty(arguments)) {
                    for (ArgumentConfig argument : arguments) {
                        // convert argument type
                        // 分支1  检测 type 属性是否为空，或者空串
                        if (argument.getType() != null && argument.getType().length() > 0) {
                            Method[] methods = interfaceClass.getMethods();
                            // visit all methods
                            if (methods.length > 0) {
                                for (int i = 0; i < methods.length; i++) {
                                    String methodName = methods[i].getName();
                                    // target the method, and get its signature
                                    // 1、比对方法名，查找目标方法 2、通过反射获取目标方法的参数类型数组 argtypes
                                    if (methodName.equals(method.getName())) {
                                        Class<?>[] argtypes = methods[i].getParameterTypes();
                                        // one callback in the method
                                        if (argument.getIndex() != -1) {
                                            // 检测 ArgumentConfig 中的 type 属性与方法参数列表
                                            // 分支2   中的参数名称是否一致，不一致则抛出异常
                                            //1. 从 argtypes 数组中获取下标 index 处的元素 argType
                                            //2. 检测 argType 的名称与 ArgumentConfig 中的 type 属性是否一致
                                            //3. 添加 ArgumentConfig 字段信息到 map 中，或抛出异常
                                            if (argtypes[argument.getIndex()].getName().equals(argument.getType())) {
                                                // 添加 ArgumentConfig 字段信息到 map 中，
                                                // 键前缀 = 方法名.index，比如:
                                                // map = {"sayHello.3": true}
                                                AbstractConfig.appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                                            } else {
                                                throw new IllegalArgumentException("Argument config error : the index attribute and type attribute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                            }
                                        } else { // 分支3 ⭐️
                                            //1. 遍历参数类型数组 argtypes，查找 argument.type 类型的参数
                                            //2. 添加 ArgumentConfig 字段信息到 map 中
                                            // multiple callbacks in the method
                                            for (int j = 0; j < argtypes.length; j++) {
                                                Class<?> argclazz = argtypes[j];
                                                // 从参数类型列表中查找类型名称为 argument.type 的参数
                                                if (argclazz.getName().equals(argument.getType())) {
                                                    AbstractConfig.appendParameters(map, argument, method.getName() + "." + j);
                                                    if (argument.getIndex() != -1 && argument.getIndex() != j) {
                                                        throw new IllegalArgumentException("Argument config error : the index attribute and type attribute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (argument.getIndex() != -1) {
                            // 用户未配置 type 属性，但配置了 index 属性，且 index != -1
                            // 分支4 ⭐️
                            // 添加 ArgumentConfig 字段信息到 map 中
                            AbstractConfig.appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                        } else {
                            throw new IllegalArgumentException("Argument config must set index or type attribute.eg: <dubbo:argument index='0' .../> or <dubbo:argument type=xxx .../>");
                        }

                    }
                }
            } // end of methods for
        }

        // 检测 generic 是否为 "true"，并根据检测结果向 map 中添加不同的信息
        if (ProtocolUtils.isGeneric(generic)) {
            map.put(GENERIC_KEY, generic);
            map.put(METHODS_KEY, ANY_VALUE);
        } else {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put(REVISION_KEY, revision);
            }
            /**
             * 为接口生成包裹类 Wrapper ,Wrapper就是interface的一个动态代理，类似于 mybatis的mapper也会生成一个
             * 只不过Dubbo用的是自己的动态代理，mybatis用的是JDK动态代理
             */
            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                // 添加方法名到 map 中，如果包含多个方法名，则用逗号隔开，比如 method = init,destroy
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                // 将逗号作为分隔符连接方法名，并将连接后的字符串放入 map 中
                // ->   haveNoReturn,setTestgaga,getTestddd,hello
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }

        /**
         * Here the token value configured by the provider is used to assign the value to ServiceConfig#token
         */
        if (ConfigUtils.isEmpty(token) && provider != null) {
            token = provider.getToken();
        }

        // 添加 token 到 map 中
        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                // 随机生成 token
                map.put(TOKEN_KEY, UUID.randomUUID().toString());
            } else {
                map.put(TOKEN_KEY, token);
            }
        }
        //init serviceMetadata attachments
        serviceMetadata.getAttachments().putAll(map);

        /**
         * List<URL> registryURLs
         * 单个 URL -> registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider
         *              &dubbo=2.0.2&id=registry1&mapping-type=metadata&mapping.type=metadata&pid=10000&qos.port=22222
         *              &registry=zookeeper&timestamp=1611892414279
         * 由此分析呢：协议的路径 就是执行动作的类
         */
        // export IP 获取 本地主机的IP,也就是要暴露接口服务所属主机的IP地址（就是netty或者tomcat绑定的 IP ）
        String host = findConfigedHosts(protocolConfig, registryURLs, map);
        // export port 获取 暴露的端口,也就是要暴露接口服务 所属 服务的 端口（就是netty或者tomcat绑定的 端口）,默认20880
        Integer port = findConfigedPorts(protocolConfig, name, map);

        // 组装 URL
        // getContextPath(protocolConfig) 获取的就是 url的path
        // 成员变量path 就是 接口名 -> org.apache.dubbo.demo.GreetingService
        // 所以最终组装的path就是 contextPath+"/"+path ，但是这里contextPath为空
        // url = dubbo://192.168.1.103:20880/org.apache.dubbo.demo.GreetingService?anyhost=true&application=demo-provider
        //            &bind.ip=192.168.1.103&bind.port=20880&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&group=greeting
        //            &interface=org.apache.dubbo.demo.GreetingService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote
        //            &methods=haveNoReturn,setTestgaga,getTestddd,hello&pid=1144&qos.port=22222&release=&revision=1.0.0
        //            &side=provider&timeout=5000&timestamp=1611924667311&version=1.0.0
        URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);

        /**
         * 前置工作做完，接下来就可以进行服务导出了。服务导出分为导出到本地 (JVM)，和导出到远程
         */
        // You can customize Configurator to append extra parameters
        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) { // 没有自定义的话，就不会进这个判断
            // 加载 ConfiguratorFactory，并生成 Configurator 实例，然后通过实例配置 url
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }

        String scope = url.getParameter(SCOPE_KEY);
        // don't export when none is configured
        // scope必须不是 none，如果 scope = none，则什么都不做,
        if (!SCOPE_NONE.equalsIgnoreCase(scope)) {
            // export to local if the config is not remote (export to remote only when config is remote)
            if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {// scope != remote，导出到本地
                /**
                 *  scope = none，不导出服务
                 *  scope != remote，导出到本地
                 *  scope != local，导出到远程
                 * 不管是导出到本地，还是远程。进行服务导出之前，均需要先创建 Invoker
                 * Invoker 是一个非常重要的模型。在服务提供端，以及服务引用端均会出现 Invoker
                 * Invoker 是实体域，它是 Dubbo 的核心模型，其它模型都向它靠扰，或转换成它，它代表一个可执行体，
                 * 可向它发起 invoke 调用，它有可能是一个本地的实现，也可能是一个远程的实现，也可能一个集群实现。
                 */
                exportLocal(url);
            }
            // export to remote if the config is not local (export to local only when config is local)
            if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {  // scope != local，导出到远程
                if (CollectionUtils.isNotEmpty(registryURLs)) {
                    for (URL registryURL : registryURLs) {
                        /**
                         * List<URL> registryURLs
                         * 单个 URL -> registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider
                         *              &dubbo=2.0.2&id=registry1&mapping-type=metadata&mapping.type=metadata&pid=10000&qos.port=22222
                         *              &registry=zookeeper&timestamp=1611892414279
                         * 由此分析呢：协议的路径 就是执行动作的类
                         */
                        //if protocol is only injvm ,not register
                        if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                            continue;
                        }
                        url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
                        // 加载监视器链接，一般为空
                        URL monitorUrl = ConfigValidationUtils.loadMonitor(this, registryURL);
                        if (monitorUrl != null) {
                            // 将监视器链接作为参数添加到 url 中
                            url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            if (url.getParameter(REGISTER_KEY, true)) {
                                logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                            } else {
                                logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                            }
                        }
                        // url = dubbo://192.168.1.103:20880/org.apache.dubbo.demo.GreetingService?anyhost=true&application=demo-provider
                        //            &bind.ip=192.168.1.103&bind.port=20880&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&group=greeting
                        //            &interface=org.apache.dubbo.demo.GreetingService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote
                        //            &methods=haveNoReturn,setTestgaga,getTestddd,hello&pid=1144&qos.port=22222&release=&revision=1.0.0
                        //            &side=provider&timeout=5000&timestamp=1611924667311&version=1.0.0
                        // For providers, this is used to enable custom proxy to generate invoker
                        // 看上面英文注释！！，如果自定义代理工厂的话，才会有这个key
                        String proxy = url.getParameter(PROXY_KEY);
                        if (StringUtils.isNotEmpty(proxy)) {
                            registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                        }
                        /**
                         *  为服务提供类(ref)生成 Invoker
                         *  Invoker 是由 ProxyFactory 创建而来，Dubbo 默认的 ProxyFactory 实现类是 JavassistProxyFactory
                         *  ref   -> GreetingServiceImpl    接口实现类
                         *  interfaceClass -> GreetingService  接口
                         *  registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()) -> :
                         *              EXPORT_KEY -> "export"
                         *              url.toFullString() -> 就是url的全路径：dubbo://192.168.1.103:20880/org.apache.dubbo.demo.GreetingService?anyhost=true......
                         *                                    这个url全路径包含了服务的IP地址，端口，接口名，方法名数组等等
                         *  registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()) 目的就是把需要暴露的服务 encode成一个value
                         *  然后作为一个export参数，拼接到registryURL后面，形如：
                         *  registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?.....&export=经过encode的完整服务
                         *  与上面exportLocal（...）对比着看
                         */
                        Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                        // DelegateProviderMetaDataInvoker 用于持有 Invoker 和 ServiceConfig
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
                        /**
                         * 导出服务，并生成 Exporter
                         * 导出服务到本地相比，导出服务到远程的过程要复杂不少，
                         * 其包含了服务导出与服务注册两个过程
                         * PROTOCOL.export(wrapperInvoker) 这行代码呢，根据dubbo-spi机制，会先 url= wrapperInvoker.getUrl(),
                         * 然后 RegistryProtocol p = url.getProtocol(),所以呢 进入RegistryProtocol的export（）方法
                         */
                        Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                        exporters.add(exporter);
                    }
                } else { // 不存在注册中心，仅导出服务
                    // url = dubbo://192.168.1.103:20880/org.apache.dubbo.demo.GreetingService?anyhost=true&application=demo-provider
                    //            &bind.ip=192.168.1.103&bind.port=20880&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&group=greeting
                    //            &interface=org.apache.dubbo.demo.GreetingService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote
                    //            &methods=haveNoReturn,setTestgaga,getTestddd,hello&pid=1144&qos.port=22222&release=&revision=1.0.0
                    //            &side=provider&timeout=5000&timestamp=1611924667311&version=1.0.0
                    // For providers, this is used to enable custom proxy to generate invoker
                    if (logger.isInfoEnabled()) {
                        logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                    }
                    Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
                    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
                    // 此时：根据dubbo-spi机制，会进入  DubboProtocol的export（）方法
                    Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                    exporters.add(exporter);
                }

                MetadataUtils.publishServiceDefinition(url);
            }
        }
        this.urls.add(url);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * always export injvm
     */
    private void exportLocal(URL url) {
        // url = dubbo://192.168.1.103:20880/org.apache.dubbo.demo.GreetingService?anyhost=true&application=demo-provider
        //            &bind.ip=192.168.1.103&bind.port=20880&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&group=greeting
        //            &interface=org.apache.dubbo.demo.GreetingService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote
        //            &methods=haveNoReturn,setTestgaga,getTestddd,hello&pid=1144&qos.port=22222&release=&revision=1.0.0
        //            &side=provider&timeout=5000&timestamp=1611924667311&version=1.0.0


       // local = injvm://127.0.0.1/org.apache.dubbo.demo.GreetingService?anyhost=true&application=demo-provider&bind.ip=192.168.1.103
        //            &bind.port=20880&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&group=greeting
        //            &interface=org.apache.dubbo.demo.GreetingService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote
        //            &methods=haveNoReturn,setTestgaga,getTestddd,hello&pid=3584&qos.port=22222&release=&revision=1.0.0&side=provider
        //            &timeout=5000&timestamp=1611925591427&version=1.0.0
        URL local = URLBuilder.from(url)
                .setProtocol(LOCAL_PROTOCOL)   // 设置协议头为 injvm.
                .setHost(LOCALHOST_VALUE) // "127.0.0.1"
                .setPort(0)
                .build();
        // 创建 Invoker，并导出服务，这里的 protocol 会在运行时调用 InjvmProtocol 的 export 方法,下面具体分析
        Exporter<?> exporter = PROTOCOL.export(
                PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local));
        exporters.add(exporter);
        /**
         * 分两段进行分析
         * 一、
         * 1、PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local)
         *       ref     ->   接口的实现类，也就是 GreetingServiceImpl
         *       interfaceClass    -> 接口  GreetingService
         * 根据DubboSPI机制，PROXY_FACTORY 是 ProxyFactory$Adaptive.java 代理类,调用的getInvoker（....）具体调用到哪个真实类 取决于
         * url.getParameter(...),local入参就是url，而 interface ProxyFactory的接口有注解@SPI("javassist")，方法有注解@Adaptive({proxy})
         * 所以  url.getParameter("proxy") 为null的时候，默认就是 javassist，也就是 会进入 JavassistProxyFactory类
         * 上面的这一块的解析，还是取决的与SPI-adaptive的原理，一定要仔细看！！
         * 2、进入 JavassistProxyFactory类，研究动态代理的产生过程
         *    最终返回一个 Invoker，但是是个匿名内部类：AbstractProxyInvoker$038483   继承 AbstractProxyInvoker
         * 二、PROTOCOL.export（invoker）
         *   上面我们已经知道了 invoker 是个匿名内部类：AbstractProxyInvoker$038483 继承 AbstractProxyInvoker
         *   调用 PROTOCOL.export（invoker），会先 url = invoker.getUrl(..)，调用的是父类AbstractProxyInvoker.getUrl(...)
         *   返回的就是 开始传入的 local = injvm://127.0.0.1/org.apache.dubbo.demo.GreetingService?....
         *   根据DubboSPI机制,PROTOCOL 是 Protocol$Adatptive.java,调用 export（invoker）具体调用到哪个真实类 取决于
         *   url.getProtcol(..),而不是取决于 url.getParameter(...) ，：具体原因看adaptive原理
         *   url.getProtcol(..) = injvm ,所以进入 InjvmProtocol 类
         */
        logger.info("Export dubbo service " + interfaceClass.getName() + " to local registry url : " + local);
    }

    /**
     * Determine if it is injvm
     *
     * @return
     */
    private boolean isOnlyInJvm() {
        return getProtocols().size() == 1
                && LOCAL_PROTOCOL.equalsIgnoreCase(getProtocols().get(0).getName());
    }


    /**
     * Register & bind IP address for service provider, can be configured separately.
     * Configuration priority: environment variables -> java system properties -> host property in config file ->
     * /etc/hosts -> default network address -> first available network address
     *
     * @param protocolConfig
     * @param registryURLs
     * @param map
     * @return
     */
    private String findConfigedHosts(ProtocolConfig protocolConfig,
                                     List<URL> registryURLs,
                                     Map<String, String> map) {
        boolean anyhost = false;
        // 从系统变量里获取，一般都是空的  -> getSystemProperty
        String hostToBind = getValueFromConfig(protocolConfig, DUBBO_IP_TO_BIND);
        if (hostToBind != null && hostToBind.length() > 0 && isInvalidLocalHost(hostToBind)) {
            throw new IllegalArgumentException("Specified invalid bind ip from property:" + DUBBO_IP_TO_BIND + ", value:" + hostToBind);
        }

        // if bind ip is not found in environment, keep looking up
        if (StringUtils.isEmpty(hostToBind)) {
            // 这是xml配置文件里，或者properties文件里指定IP,当然一般也不会指定的，所以也是空
            hostToBind = protocolConfig.getHost();
            if (provider != null && StringUtils.isEmpty(hostToBind)) {
                hostToBind = provider.getHost();
            }
            if (isInvalidLocalHost(hostToBind)) {
                // 如果从系统变量里获取不到，程序员也没有手动指定IP，那就动态获取本机IP
                anyhost = true;
                try {
                    logger.info("No valid ip found from environment, try to find valid host from DNS.");
                    // 动态获取本机IP,这里肯定不是空
                    hostToBind = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    logger.warn(e.getMessage(), e);
                }
                // 这个判断不会成立，因为上面已经从本地主机获取到了 IP
                if (isInvalidLocalHost(hostToBind)) {
                    if (CollectionUtils.isNotEmpty(registryURLs)) {
                        for (URL registryURL : registryURLs) {
                            if (MULTICAST.equalsIgnoreCase(registryURL.getParameter("registry"))) {
                                // skip multicast registry since we cannot connect to it via Socket
                                continue;
                            }
                            try (Socket socket = new Socket()) {
                                SocketAddress addr = new InetSocketAddress(registryURL.getHost(), registryURL.getPort());
                                socket.connect(addr, 1000);
                                hostToBind = socket.getLocalAddress().getHostAddress();
                                break;
                            } catch (Exception e) {
                                logger.warn(e.getMessage(), e);
                            }
                        }
                    }
                    if (isInvalidLocalHost(hostToBind)) {
                        hostToBind = getLocalHost();
                    }
                }
            }
        }

        map.put(BIND_IP_KEY, hostToBind);

        // registry ip is not used for bind ip by default
        // 也是从 系统环境变量里 获取，当时一般也不会配置，所以也是空
        String hostToRegistry = getValueFromConfig(protocolConfig, DUBBO_IP_TO_REGISTRY);
        if (hostToRegistry != null && hostToRegistry.length() > 0 && isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        } else if (StringUtils.isEmpty(hostToRegistry)) {
            // bind ip is used as registry ip by default
            hostToRegistry = hostToBind;
        }

        map.put(ANYHOST_KEY, String.valueOf(anyhost));

        return hostToRegistry;
    }


    /**
     * Register port and bind port for the provider, can be configured separately
     * Configuration priority: environment variable -> java system properties -> port property in protocol config file
     * -> protocol default port
     *
     * @param protocolConfig
     * @param name
     * @return
     */
    private Integer findConfigedPorts(ProtocolConfig protocolConfig,
                                      String name,
                                      Map<String, String> map) {
        Integer portToBind = null;

        // parse bind port from environment
        String port = getValueFromConfig(protocolConfig, DUBBO_PORT_TO_BIND);
        portToBind = parsePort(port);

        // if there's no bind port found from environment, keep looking up.
        if (portToBind == null) {
            portToBind = protocolConfig.getPort();
            if (provider != null && (portToBind == null || portToBind == 0)) {
                portToBind = provider.getPort();
            }
            final int defaultPort = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name).getDefaultPort();
            if (portToBind == null || portToBind == 0) {
                portToBind = defaultPort;
            }
            if (portToBind <= 0) {
                portToBind = getRandomPort(name);
                if (portToBind == null || portToBind < 0) {
                    portToBind = getAvailablePort(defaultPort);
                    putRandomPort(name, portToBind);
                }
            }
        }

        // save bind port, used as url's key later
        map.put(BIND_PORT_KEY, String.valueOf(portToBind));

        // registry port, not used as bind port by default
        String portToRegistryStr = getValueFromConfig(protocolConfig, DUBBO_PORT_TO_REGISTRY);
        Integer portToRegistry = parsePort(portToRegistryStr);
        if (portToRegistry == null) {
            portToRegistry = portToBind;
        }

        return portToRegistry;
    }

    private Integer parsePort(String configPort) {
        Integer port = null;
        if (configPort != null && configPort.length() > 0) {
            try {
                Integer intPort = Integer.parseInt(configPort);
                if (isInvalidPort(intPort)) {
                    throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
                }
                port = intPort;
            } catch (Exception e) {
                throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
            }
        }
        return port;
    }

    private String getValueFromConfig(ProtocolConfig protocolConfig, String key) {
        String protocolPrefix = protocolConfig.getName().toUpperCase() + "_";
        String value = ConfigUtils.getSystemProperty(protocolPrefix + key);
        if (StringUtils.isEmpty(value)) {
            value = ConfigUtils.getSystemProperty(key);
        }
        return value;
    }

    private Integer getRandomPort(String protocol) {
        protocol = protocol.toLowerCase();
        return RANDOM_PORT_MAP.getOrDefault(protocol, Integer.MIN_VALUE);
    }

    private void putRandomPort(String protocol, Integer port) {
        protocol = protocol.toLowerCase();
        if (!RANDOM_PORT_MAP.containsKey(protocol)) {
            RANDOM_PORT_MAP.put(protocol, port);
            logger.warn("Use random available port(" + port + ") for protocol " + protocol);
        }
    }

    private void postProcessConfig() {
        List<ConfigPostProcessor> configPostProcessors = ExtensionLoader.getExtensionLoader(ConfigPostProcessor.class)
                .getActivateExtension(URL.valueOf("configPostProcessor://"), (String[]) null);
        configPostProcessors.forEach(component -> component.postProcessServiceConfig(this));
    }

    /**
     * Dispatch an {@link Event event}
     *
     * @param event an {@link Event event}
     * @since 2.7.5
     */
    private void dispatch(Event event) {
        EventDispatcher.getDefaultExtension().dispatch(event);
    }

    public DubboBootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }
}
