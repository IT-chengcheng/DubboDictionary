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
package org.apache.dubbo.remoting.exchange.support.header;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.ExecutionException;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.remoting.transport.ChannelHandlerDelegate;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

import static org.apache.dubbo.common.constants.CommonConstants.READONLY_EVENT;


/**
 * ExchangeReceiver
 */
public class HeaderExchangeHandler implements ChannelHandlerDelegate {

    protected static final Logger logger = LoggerFactory.getLogger(HeaderExchangeHandler.class);

    private final ExchangeHandler handler;

    public HeaderExchangeHandler(ExchangeHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.handler = handler;
    }

    static void handleResponse(Channel channel, Response response) throws RemotingException {
        if (response != null && !response.isHeartbeat()) {
            // 核心，重点！！！
            DefaultFuture.received(channel, response);
            /**
             * 一、响应数据解码完成后，Dubbo 会将响应对象派发到线程池上。要注意的是，线程池中的线程并非用户的调用线程，
             *     所以要想办法将响应对象从线程池线程传递到用户线程上，怎么才能做到呢：
             * 二、这就用到了 JDK提供的 CompletableFuture,dubbo自定义的 DefaultFuture 继承了  CompletableFuture
             *     不只是这里用到了 CompletableFuture，Dubbo有很多地方都是用了CompletableFuture，这是JDK1.8新特性
             * 三、下面来解释，怎么做到的：
             *    用户线程在发送完请求后的动作，做了两件事
             *     1、将本次发送消息相关联的DefaultFuture对象放到 一个全局共享内存中Map<Long, DefaultFuture> FUTURES = new ConcurrentHashMap<>();
             *         这个属性在DefaultFuture中，是个static final。key是本次请求的id=request.getId()  value=DefaultFuture对象
             *     2、立即将DefaultFuture对象返回
             *         xml配置的异步，将 DefaultFuture对象返回给程序员，程序员执行 DefaultFuture.get（）进行阻塞 等待结果
             *         xml默认是同步 ，dubbo内部执行 DefaultFuture.get（）进行阻塞 等待结果
             *
             *    当响应对象到来后，到达本方法 handleResponse（），然后调用 DefaultFuture.received(channel, response);
             *     这个方法主要做了两件事：
             *       1、根据 response.getID从 FUTURES 中取到 DefaultFuture对象  （responseID的值就是consumer传给provider的requestID）
             *       2、执行 DefaultFuture.complete(响应结果)，此时  用户线程会被唤醒，也就给程序员返回结果了
             */
        }
    }

    private static boolean isClientSide(Channel channel) {
        InetSocketAddress address = channel.getRemoteAddress();
        URL url = channel.getUrl();
        return url.getPort() == address.getPort() &&
                NetUtils.filterLocalHost(url.getIp())
                        .equals(NetUtils.filterLocalHost(address.getAddress().getHostAddress()));
    }

    void handlerEvent(Channel channel, Request req) throws RemotingException {
        if (req.getData() != null && req.getData().equals(READONLY_EVENT)) {
            channel.setAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY, Boolean.TRUE);
        }
    }

    void handleRequest(final ExchangeChannel channel, Request req) throws RemotingException {
        // 会传入request对象的id，这个id是从consumer发过来的，然后在赋值给response，目的是为了利用这个ID做一个牵线
        // 让consumer的用户线程 跟 consumer的接收读写事件线程 进行通信
        Response res = new Response(req.getId(), req.getVersion());
        if (req.isBroken()) {
            // 检测请求是否合法，不合法则返回状态码为 BAD_REQUEST 的响应
            Object data = req.getData();

            String msg;
            if (data == null) {
                msg = null;
            } else if (data instanceof Throwable) {
                msg = StringUtils.toString((Throwable) data);
            } else {
                msg = data.toString();
            }
            res.setErrorMessage("Fail to decode request due to: " + msg);
            res.setStatus(Response.BAD_REQUEST);

            channel.send(res);
            return;
        }
        // find handler by message class.
        // 获取 data 字段值，也就是 RpcInvocation 对象
        Object msg = req.getData();
        try {
            // handler = DubboProtocol$1@3293 ,是DubboProtocol的一个匿名内部类
            CompletionStage<Object> future = handler.reply(channel, msg);
            future.whenComplete((appResult, t) -> {
                try {
                    if (t == null) {
                        res.setStatus(Response.OK);
                        res.setResult(appResult);
                    } else {
                        res.setStatus(Response.SERVICE_ERROR);
                        res.setErrorMessage(StringUtils.toString(t));
                    }
                    // 将调用结果返回给服务消费端
                   // channel = HeaderExchangeChannel(NettyChannel(NioSocketChannel()))
                    channel.send(res);
                } catch (RemotingException e) {
                    logger.warn("Send result to consumer failed, channel is " + channel + ", msg is " + e);
                }
            });
        } catch (Throwable e) {
            res.setStatus(Response.SERVICE_ERROR);
            res.setErrorMessage(StringUtils.toString(e));
            channel.send(res);
        }
    }

    @Override
    public void connected(Channel channel) throws RemotingException {
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        handler.connected(exchangeChannel);
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            handler.disconnected(exchangeChannel);
        } finally {
            DefaultFuture.closeChannel(channel);
            HeaderExchangeChannel.removeChannel(channel);
        }
    }

    @Override
    public void sent(Channel channel, Object message) throws RemotingException {
        Throwable exception = null;
        try {
            ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
            handler.sent(exchangeChannel, message);
        } catch (Throwable t) {
            exception = t;
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
        if (message instanceof Request) {
            Request request = (Request) message;
            DefaultFuture.sent(channel, request);
        }
        if (exception != null) {
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            } else if (exception instanceof RemotingException) {
                throw (RemotingException) exception;
            } else {
                throw new RemotingException(channel.getLocalAddress(), channel.getRemoteAddress(),
                        exception.getMessage(), exception);
            }
        }
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        // channel = NettyChannel(NioSocketChannel())
        final ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        // 处理请求对象
        if (message instanceof Request) {
            // handle request.
            Request request = (Request) message;
            if (request.isEvent()) {
                // 处理事件
                handlerEvent(channel, request);
            } else {  // 处理普通的请求
                // 双向通信
                if (request.isTwoWay()) {
                    handleRequest(exchangeChannel, request);
                } else {
                    // 如果是单向通信，仅向后调用指定服务即可，无需返回调用结果
                    handler.received(exchangeChannel, request.getData());
                }
            }
        } else if (message instanceof Response) {
            // 处理响应对象，服务消费方会执行此处逻辑
            handleResponse(channel, (Response) message);
        } else if (message instanceof String) {
            if (isClientSide(channel)) {
                Exception e = new Exception("Dubbo client can not supported string message: " + message + " in channel: " + channel + ", url: " + channel.getUrl());
                logger.error(e.getMessage(), e);
            } else {
                String echo = handler.telnet(channel, (String) message);
                if (echo != null && echo.length() > 0) {
                    channel.send(echo);
                }
            }
        } else {
            handler.received(exchangeChannel, message);
        }
    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        if (exception instanceof ExecutionException) {
            ExecutionException e = (ExecutionException) exception;
            Object msg = e.getRequest();
            if (msg instanceof Request) {
                Request req = (Request) msg;
                if (req.isTwoWay() && !req.isHeartbeat()) {
                    Response res = new Response(req.getId(), req.getVersion());
                    res.setStatus(Response.SERVER_ERROR);
                    res.setErrorMessage(StringUtils.toString(e));
                    channel.send(res);
                    return;
                }
            }
        }
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            handler.caught(exchangeChannel, exception);
        } finally {
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
    }

    @Override
    public ChannelHandler getHandler() {
        if (handler instanceof ChannelHandlerDelegate) {
            return ((ChannelHandlerDelegate) handler).getHandler();
        } else {
            return handler;
        }
    }
}
