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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.consistency.weak.tree.remoting.BoltRpcClientProxy;
import com.alibaba.nacos.naming.consistency.weak.tree.remoting.BoltRpcServerProxy;
import com.alibaba.nacos.naming.consistency.weak.tree.remoting.RpcRequestMessage;
import com.alibaba.nacos.naming.consistency.weak.tree.remoting.ServerUserProcessor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author satjd
 */
@Component
public class TransferTaskSingleProcessor implements Runnable{

    @Autowired
    private ProtocolConfig protocolConfig;

    @Autowired
    private TreePeerSet treePeerSet;

    @Autowired
    ServerUserProcessor serverUserProcessor;

    private BoltRpcClientProxy clientProxy;
    private BoltRpcServerProxy serverProxy;

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.tree.subscribemanager.taskprocesser.single");

            return t;
        }
    });

    private final LinkedBlockingQueue<TransferTask> taskHolder = new LinkedBlockingQueue<>();

    @PostConstruct
    private void init() {
        scheduler.schedule(this,100L, TimeUnit.MILLISECONDS);
        clientProxy = new BoltRpcClientProxy();
        serverProxy = new BoltRpcServerProxy(protocolConfig.getBoltPort());
        serverProxy.registerUserProcessor(serverUserProcessor);
        if (!serverProxy.start()) {
            Loggers.TREE.error("Bolt Rpc server failed to start!");
        }
    }

    public void addTask(TransferTask task) {
        while (!taskHolder.offer(task)) {
            Loggers.TREE.warn("Retry add transfer task");
        }
    }

    private void sendDatum(TransferTask task) {
        JSONObject json = new JSONObject();
        json.put("datum", task.datum);
        json.put("source", task.source);

        String body = json.toJSONString();

        Set<TreePeer> curChild = treePeerSet.getCurrentChild(task.source);

        // todo change log level from INFO to DEBUG
        if (Loggers.TREE.isDebugEnabled()) {
            Loggers.TREE.debug("Forward to:" + curChild.toString());
        }

        RpcRequestMessage msg = new RpcRequestMessage();
        msg.type = task.datumType;
        msg.payload = body.getBytes();

        for (TreePeer child : curChild) {
            String addr = child.ip
                + UtilsAndCommons.IP_PORT_SPLITER
                + protocolConfig.getBoltPort();

            if (!clientProxy.invokeSync(addr, msg, 60000)) {
                // if invoke failed, retry this invocation
                addTask(task);
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            TransferTask task = null;
            try {
                task = taskHolder.take();
            } catch (InterruptedException e) {
                Loggers.TREE.error(e.toString());
            }

            if (task == null) {
                continue;
            }

            sendDatum(task);
        }
    }
}
