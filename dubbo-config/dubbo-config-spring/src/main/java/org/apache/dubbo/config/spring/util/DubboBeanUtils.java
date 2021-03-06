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
package org.apache.dubbo.config.spring.util;

import org.apache.dubbo.config.spring.beans.factory.annotation.DubboConfigAliasPostProcessor;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.config.spring.beans.factory.config.DubboConfigDefaultPropertyValueBeanPostProcessor;
import org.apache.dubbo.config.spring.beans.factory.config.DubboConfigEarlyInitializationPostProcessor;
import org.apache.dubbo.config.spring.context.DubboApplicationListenerRegistrar;
import org.apache.dubbo.config.spring.context.DubboBootstrapApplicationListener;
import org.apache.dubbo.config.spring.context.DubboLifecycleComponentApplicationListener;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import static com.alibaba.spring.util.BeanRegistrar.registerInfrastructureBean;

/**
 * Dubbo Bean utilities class
 *
 * @since 2.7.6
 */
public interface DubboBeanUtils {

    /**
     * Register the common beans
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @see ReferenceAnnotationBeanPostProcessor
     * @see DubboConfigDefaultPropertyValueBeanPostProcessor
     * @see DubboConfigAliasPostProcessor
     * @see DubboLifecycleComponentApplicationListener
     * @see DubboBootstrapApplicationListener
     */
    // 这个方法dubbo会调用两次，不知道为啥要这么设计！！！！！！，虽然是幂等操作
    static void registerCommonBeans(BeanDefinitionRegistry registry) {
        /**
         * 重点来了，这些注册的 bd，一定要挨个仔细看！！！
         */
        // Since 2.5.7 Register @Reference Annotation Bean Processor as an infrastructure Bean
        /**
         *  去看父类 AbstractAnnotationBeanPostProcessor ->  postProcessPropertyValues(.....)的方法 ，这是触发点
         *  ReferenceAnnotationBeanPostProcessor extends   abstract AbstractAnnotationBeanPostProcessor
         *    extends InstantiationAwareBeanPostProcessorAdapter  implements InstantiationAwareBeanPostProcessor
         *
         *   spring实例化完bean后，会调用 postProcessPropertyValues(.....) ,进入AbstractAnnotationBeanPostProcessor的postProcessPropertyValues(.....)
         * 父类做的主要是找到加了注解 @Refrence 的属性，然后进行注入，最终调用的是 ReferenceAnnotationBeanPostProcessor.doGetInjectedBean()
         * 所以进入该方法，仔细看就行了。
         *
         * 重点来了：
         * 这两个类是dubbo自定义的： ReferenceAnnotationBeanPostProcessor extends abstract AbstractAnnotationBeanPostProcessor
         * 而且最终继承了 spring的  InstantiationAwareBeanPostProcessor。
         * Spring默认InstantiationAwareBeanPostProcessor的实现类 是
         *    CommonAnnotationBeanPostProcessor   处理@Resource注解
         *    AutowiredAnnotationBeanPostProcessor  处理@Autowire注解
         * 而 dubbo自定义的实现类 负责处理 dubbo自己的 @Refrence 注解
         * 所以这些逻辑都是一样的，可以详细去看spring源码！！！！
         */
        registerInfrastructureBean(registry, ReferenceAnnotationBeanPostProcessor.BEAN_NAME,
                ReferenceAnnotationBeanPostProcessor.class);

        // Since 2.7.4 [Feature] https://github.com/apache/dubbo/issues/5093
        registerInfrastructureBean(registry, DubboConfigAliasPostProcessor.BEAN_NAME,
                DubboConfigAliasPostProcessor.class);

        // Since 2.7.9 Register DubboApplicationListenerRegister as an infrastructure Bean
        // https://github.com/apache/dubbo/issues/6559

        // Since 2.7.5 Register DubboLifecycleComponentApplicationListener as an infrastructure Bean
        // registerInfrastructureBean(registry, DubboLifecycleComponentApplicationListener.BEAN_NAME,
        //        DubboLifecycleComponentApplicationListener.class);

        // Since 2.7.4 Register DubboBootstrapApplicationListener as an infrastructure Bean
        // registerInfrastructureBean(registry, DubboBootstrapApplicationListener.BEAN_NAME,
        //        DubboBootstrapApplicationListener.class);

        registerInfrastructureBean(registry, DubboApplicationListenerRegistrar.BEAN_NAME,
                DubboApplicationListenerRegistrar.class);

        // Since 2.7.6 Register DubboConfigDefaultPropertyValueBeanPostProcessor as an infrastructure Bean
        registerInfrastructureBean(registry, DubboConfigDefaultPropertyValueBeanPostProcessor.BEAN_NAME,
                DubboConfigDefaultPropertyValueBeanPostProcessor.class);

        // Since 2.7.9 Register DubboConfigEarlyInitializationPostProcessor as an infrastructure Bean
        registerInfrastructureBean(registry, DubboConfigEarlyInitializationPostProcessor.BEAN_NAME,
                DubboConfigEarlyInitializationPostProcessor.class);
    }
}
