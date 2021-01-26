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
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.dubbo.config.AbstractConfig;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static com.alibaba.spring.util.AnnotatedBeanDefinitionRegistryUtils.registerBeans;
import static org.apache.dubbo.config.spring.util.DubboBeanUtils.registerCommonBeans;

/**
 * Dubbo {@link AbstractConfig Config} {@link ImportBeanDefinitionRegistrar register}, which order can be configured
 *
 * @see EnableDubboConfig
 * @see DubboConfigConfiguration
 * @see Ordered
 * @since 2.5.8
 */
public class DubboConfigConfigurationRegistrar implements ImportBeanDefinitionRegistrar, ApplicationContextAware {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        /**
         * 会对Properties文件进行解析，主要完成的事情是根据Properties文件的每个配置项的前缀、参数名、参数值生成对应的Bean。
         *  比如前缀为"dubbo.application"的配置项，会生成一个 ApplicationConfig  类型的BeanDefinition。
         *  比如前缀为"dubbo.protocol"的配置项，会生成一个  ProtocolConfig  类型的BeanDefinition。
         *
         *  都有哪些配置类？ 具体看：
         *    1、DubboConfigConfiguration.Single
         *    2、DubboConfigConfiguration.Multiple
         */
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConfig.class.getName()));

        boolean multiple = attributes.getBoolean("multiple");

        // Single Config Bindings
        registerBeans(registry, DubboConfigConfiguration.Single.class);

        if (multiple) { // Since 2.6.6 https://github.com/apache/dubbo/issues/3193
            /**
             * 默认情况下开启了multiple模式，multiple模式表示开启多配置模式，意思是这样的：
               1、如果没有开启multiple模式，那么只支持配置一个dubbo.protocol，比如：
                         dubbo.protocol.name=dubbo
                         dubbo.protocol.port=20880
                         dubbo.protocol.host=0.0.0.0
               2、如果开启了multiple模式，那么可以支持多个dubbo.protocol，比如：
                        dubbo.protocols.p1.name=dubbo
                        dubbo.protocols.p1.port=20880
                        dubbo.protocols.p1.host=0.0.0.0

                        dubbo.protocols.p2.name=http
                        dubbo.protocols.p2.port=8082
                        dubbo.protocols.p2.host=0.0.0.0
             */
            registerBeans(registry, DubboConfigConfiguration.Multiple.class);
        }

        // Since 2.7.6
        registerCommonBeans(registry);

        /**
         *1. 根据DubboConfigConfiguration.Single.class的定义来注册BeanDefinition，如果开启了multiple模式，
         *    则根据DubboConfigConfiguration.Multiple.class的定义来注册BeanDefinition
         2. 两者都是调用的registerBeans(BeanDefinitionRegistry registry, Class<?>... annotatedClasses)方法
         3. 在registerBeans方法内，会利用Spring中的AnnotatedBeanDefinitionReader类来加载annotatedClasses参数所指定的类
              （上面所Single或Multiple类），Spring的AnnotatedBeanDefinitionReader类会识别annotatedClasses上的注解，
                   然后开启解析annotatedClasses类上的注解
         4. 可以发现，不管是Single类，还是Multiple类，类上面定义的注解都是@EnableDubboConfigBindings，
              所以Spring会解析这个注解，在这个注解的定义上Import了一个DubboConfigBindingsRegistrar类，
              所以这是Spring会去调用DubboConfigBindingsRegistrar类的registerBeanDefinitions方法
         5. 在DubboConfigBindingsRegistrar类的registerBeanDefinitions方法中，会去取EnableDubboConfigBindings注解的value属性的值，
                该值是一个数组，数组中存的内容为@EnableDubboConfigBinding注解。此时DubboConfigBindingsRegistrar会去处理
                   各个@EnableDubboConfigBinding注解，
          使用DubboConfigBindingRegistrar类的registerBeanDefinitions(AnnotationAttributes attributes, BeanDefinitionRegistry registry)
             去处理各个@EnableDubboConfigBinding注解
         6. attributes表示@EnableDubboConfigBinding注解中的参数对，比如prefix = "dubbo.application", type = ApplicationConfig.class。
         7. 获取出对应当前@EnableDubboConfigBinding注解的prefix和AbstractConfig类，
                ApplicationConfig、RegistryConfig、ProtocolConfig等等都是AbstractConfig类的子类
         8. 从environment.getPropertySources()中获取对应的prefix的properties，相当于从Properties文件中获取对应prefix的属性，
                后面在生成了AplicationConfig类型的BeanDefinition之后，会把这些属性值赋值给对应的BeanDefinition，
               但是这里获取属性只是为了获取bd Name
         9. 在生成bd之前，需要确定Bean的名字，可以通过在Properties文件中配置相关的id属性，那么则对应的值就为beanName，
               否则自动生成一个beanName (注意：不是取得配置文件的 name属性 ，而是id)
         10. 对于Multiple模式的配置，会存在多个bean以及多个beanName
         11. 得到beanName之后，向Spring中注册一个空的BeanDefinition对象，并且向Spring中添加
                  一个DubboConfigBindingBeanPostProcessor(Bean后置处理器)，
         13. 至此，Spring扫描逻辑走完了。
         14. 接下来，Spring会根据生成的BeanDefinition生成一个对象，然后会经过DubboConfigBindingBeanPostProcessor后置处理器的处理。
         15. DubboConfigBindingBeanPostProcessor主要用来对其对应的bean进行属性赋值
         16. 首先通过DubboConfigBinder的默认实现类DefaultDubboConfigBinder，来从Properties文件中获取prefix对应的属性值，
                然后把这些属性值赋值给AbstractConfig对象中的属性
         17. 然后看AbstractConfig类中是否存在setName()方法，如果存在则把beanName设置进去
         18. 这样一个AbstractConfig类的bean就生成好了
         19. 总结一下：Spring在启动时，会去生成ApplicationConfig、RegistryConfig、ProtocolConfig等等AbstractConfig子类的bean对象，
           然后从Properties文件中获取属性值并赋值到bean对象中去。
         DubboConfigConfigurationRegistrar的逻辑整理完后，就开始整理DubboComponentScanRegistrar的逻辑
         *
         *
         */
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (!(applicationContext instanceof ConfigurableApplicationContext)) {
            throw new IllegalArgumentException("The argument of ApplicationContext must be ConfigurableApplicationContext");
        }
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }
}
