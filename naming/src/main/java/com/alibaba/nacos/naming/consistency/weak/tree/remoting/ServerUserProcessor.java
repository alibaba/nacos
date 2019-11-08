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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.weak.tree.DatumType;
import com.alibaba.nacos.naming.consistency.weak.tree.TreeBasedConsistencyServiceImpl;
import com.alibaba.nacos.naming.consistency.weak.tree.TreePeer;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.NamedThreadFactory;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author satjd
 */
@Component
public class ServerUserProcessor extends SyncUserProcessor<RpcRequestMessage> {

    /** logger */
    private static final Logger logger         = Loggers.TREE;

    /** executor */
    private ThreadPoolExecutor  executor;

    /** default is true */
    private boolean             timeoutDiscard = true;

    @Autowired
    TreeBasedConsistencyServiceImpl treeBasedConsistencyService;

    public ServerUserProcessor() {
        this(100, 100, 60, 1000);
    }

    public ServerUserProcessor(int core, int max, int keepaliveSeconds,
                               int workQueue) {
        this.executor = new ThreadPoolExecutor(core, max, keepaliveSeconds, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(workQueue), new NamedThreadFactory(
                "Request-process-pool"));
    }

    // ~~~ override methods

    @Override
    public Object handleRequest(BizContext bizCtx, RpcRequestMessage requestMessage) throws Exception {
        logger.info("Request received:" + requestMessage + ", timeout:" + bizCtx.getClientTimeout()
                    + ", arriveTimestamp:" + bizCtx.getArriveTimestamp() + ", remote address:" + bizCtx.getRemoteAddress());

        if (bizCtx.isRequestTimeout()) {
            String errMsg = "Stop process in server biz thread, already timeout!";
            logger.warn(errMsg);
            throw new Exception(errMsg);
        }

        //test biz context get connection
        if (bizCtx.getConnection() == null || !bizCtx.getConnection().isFine()) {
            logger.warn("Connection is unhealthy. Remote address is [" + bizCtx.getRemoteAddress() + "].");
        }

        Long waittime = (Long) bizCtx.getInvokeContext().get(InvokeContext.BOLT_PROCESS_WAIT_TIME);

        if (logger.isDebugEnabled()) {
            logger.debug("Server User processor process wait time {}", waittime);
            logger.debug("Server User processor say, remote address is [" + bizCtx.getRemoteAddress() + "].");
        }

        handleMessage(requestMessage);

        return "Server ok.";
    }

    @Override
    public String interest() {
        return RpcRequestMessage.class.getName();
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    @Override
    public boolean timeoutDiscard() {
        return this.timeoutDiscard;
    }

    /**
     * Getter method for property <tt>timeoutDiscard</tt>.
     *
     * @return property value of timeoutDiscard
     */
    public boolean isTimeoutDiscard() {
        return timeoutDiscard;
    }

    /**
     * Setter method for property <tt>timeoutDiscard<tt>.
     *
     * @param timeoutDiscard value to be assigned to property timeoutDiscard
     */
    public void setTimeoutDiscard(boolean timeoutDiscard) {
        this.timeoutDiscard = timeoutDiscard;
    }

    private void handleMessage(RpcRequestMessage requestMessage) {
        try {
            String entity = new String(requestMessage.payload);
            String value = URLDecoder.decode(entity, "UTF-8");
            JSONObject jsonObject = JSON.parseObject(value);
            String key = "key";

            TreePeer source = JSON.parseObject(jsonObject.getString("source"), TreePeer.class);
            JSONObject datumJson = jsonObject.getJSONObject("datum");

            Datum datum = null;
            if (KeyBuilder.matchInstanceListKey(datumJson.getString(key))) {
                datum = JSON.parseObject(jsonObject.getString("datum"), new TypeReference<Datum<Instances>>() {
                });
            } else if (KeyBuilder.matchSwitchKey(datumJson.getString(key))) {
                datum = JSON.parseObject(jsonObject.getString("datum"), new TypeReference<Datum<SwitchDomain>>() {
                });
            } else if (KeyBuilder.matchServiceMetaKey(datumJson.getString(key))) {
                datum = JSON.parseObject(jsonObject.getString("datum"), new TypeReference<Datum<Service>>() {
                });
            }

            if (requestMessage.type == DatumType.UPDATE) {
                treeBasedConsistencyService.onPut(datum, source);
            } else if (requestMessage.type == DatumType.DELETE) {
                treeBasedConsistencyService.onRemove(datum,source);
            }
        } catch (Exception e) {
            Loggers.TREE.error("Exception during handle request.", e);
        }
    }
}
