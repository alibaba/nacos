/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.naming.consistency.weak.tree.remoting;

import com.alibaba.nacos.naming.misc.Loggers;
import com.alipay.remoting.ConnectionEventType;
import com.alipay.remoting.InvokeCallback;
import com.alipay.remoting.NamedThreadFactory;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.rpc.RpcClient;
import com.alipay.remoting.rpc.protocol.AbstractUserProcessor;
import org.slf4j.Logger;

import java.util.concurrent.*;

/**
 * @author satjd
 */
public class BoltRpcClientProxy {
    private static Logger logger = Loggers.TREE;

    private RpcClient client;

    ConnectEventProcessor clientConnectProcessor    = new ConnectEventProcessor();
    DisconnectEventProcessor clientDisConnectProcessor = new DisconnectEventProcessor();

    public BoltRpcClientProxy() {
        // 1. create a rpc client
        client = new RpcClient();
        // 2. add processor for connect and close event if you need
        client.addConnectionEventProcessor(ConnectionEventType.CONNECT, clientConnectProcessor);
        client.addConnectionEventProcessor(ConnectionEventType.CLOSE, clientDisConnectProcessor);
        // 3. do init
        client.init();
    }

    public BoltRpcClientProxy(AbstractUserProcessor processor) {
        // 1. create a rpc client
        client = new RpcClient();
        // 2. add processor for connect and close event if you need
        client.addConnectionEventProcessor(ConnectionEventType.CONNECT, clientConnectProcessor);
        client.addConnectionEventProcessor(ConnectionEventType.CLOSE, clientDisConnectProcessor);

        client.registerUserProcessor(processor);
        // 3. do init
        client.init();
    }

    public boolean invokeSync(String addr, RpcRequestMessage msg, int timeoutMillis) {
        boolean success = true;
        try {
            String res = (String) client.invokeSync(addr, msg, timeoutMillis);
            logger.info("invoke sync result = [" + res + "]");
        } catch (RemotingException e) {
            String errMsg = "RemotingException caught in sync!";
            success = false;
            logger.error(errMsg, e);
        } catch (InterruptedException e) {
            logger.error("interrupted!");
        }

        return success;
    }

    public void invokeAsync(String addr, RpcRequestMessage msg, int timeoutMillis) {
        try {
            client.invokeWithCallback(addr, msg, new InvokeCallback() {
                Executor executor = new ThreadPoolExecutor(0, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("Async-callback-thread"));

                @Override
                public void onResponse(Object result) {
                    logger.debug("Result received in callback: " + result);
                }

                @Override
                public void onException(Throwable e) {
                    logger.error("Process exception in callback.", e);
                }

                @Override
                public Executor getExecutor() {
                    return executor;
                }

            }, timeoutMillis);

        } catch (RemotingException e) {
            String errMsg = "RemotingException caught in callback!";
            logger.error(errMsg, e);
        } catch (InterruptedException e) {
            String errMsg = "InterruptedException caught in callback!";
            logger.error(errMsg, e);
        }
    }
}
