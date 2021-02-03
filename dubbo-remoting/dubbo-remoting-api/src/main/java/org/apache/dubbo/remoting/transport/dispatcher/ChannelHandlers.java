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
package org.apache.dubbo.remoting.transport.dispatcher;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Dispatcher;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeHandler;
import org.apache.dubbo.remoting.exchange.support.header.HeartbeatHandler;
import org.apache.dubbo.remoting.transport.DecodeHandler;
import org.apache.dubbo.remoting.transport.MultiMessageHandler;
import org.apache.dubbo.remoting.transport.dispatcher.all.AllChannelHandler;

public class ChannelHandlers {

    private static ChannelHandlers INSTANCE = new ChannelHandlers();

    protected ChannelHandlers() {
    }

    public static ChannelHandler wrap(ChannelHandler handler, URL url) {
        return ChannelHandlers.getInstance().wrapInternal(handler, url);
    }

    protected static ChannelHandlers getInstance() {
        return INSTANCE;
    }

    static void setTestingChannelHandlers(ChannelHandlers instance) {
        INSTANCE = instance;
    }

    protected ChannelHandler wrapInternal(ChannelHandler handler, URL url) {
        /**
         * Dispatcher 真实的职责创建具有线程派发能力的 ChannelHandler，
         * 比如 AllChannelHandler、MessageOnlyChannelHandler 和 ExecutionChannelHandler 等，其本身并不具备线程派发能力。
         * Dubbo 支持 5 种不同的线程派发策略
         *  策略	                   用途
           all	        所有消息都派发到线程池，包括请求，响应，连接事件，断开事件等
           direct	        所有消息都不派发到线程池，全部在 IO 线程上直接执行
           message	   只有请求和响应消息派发到线程池，其它消息均在 IO 线程上执行
          execution	   只有请求消息派发到线程池，不含响应。其它消息均在 IO 线程上执行
          connection	   在 IO 线程上，将连接断开事件放入队列，有序逐个执行，其它消息派发到线程池
         */
        return new MultiMessageHandler(new HeartbeatHandler(ExtensionLoader.getExtensionLoader(Dispatcher.class)
                .getAdaptiveExtension().dispatch(handler, url)));

    }
}
