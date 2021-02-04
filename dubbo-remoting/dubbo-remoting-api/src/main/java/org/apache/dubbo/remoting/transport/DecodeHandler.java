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

package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Decodeable;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;

public class DecodeHandler extends AbstractChannelHandlerDelegate {

    private static final Logger log = LoggerFactory.getLogger(DecodeHandler.class);

    public DecodeHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        if (message instanceof Decodeable) {
            // 对 Decodeable 接口实现类对象进行解码
            decode(message);
        }

        if (message instanceof Request) {
            // 对 Request 的 data 字段进行解码
            decode(((Request) message).getData());
        }

        if (message instanceof Response) {
            // 对 Request 的 result 字段进行解码
            decode(((Response) message).getResult());
        }

       // 解码完毕后，完全解码后的 Request 对象会继续向后传递，下一站是 HeaderExchangeHandler
        handler.received(channel, message);
    }

    private void decode(Object message) {
        // Decodeable 接口目前有两个实现类，
        // 分别为 DecodeableRpcInvocation 和 DecodeableRpcResult
        if (message instanceof Decodeable) {
            try {
                ((Decodeable) message).decode();
                if (log.isDebugEnabled()) {
                    log.debug("Decode decodeable message " + message.getClass().getName());
                }
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn("Call Decodeable.decode failed: " + e.getMessage(), e);
                }
            } // ~ end of catch
        } // ~ end of if
    } // ~ end of method decode

}
