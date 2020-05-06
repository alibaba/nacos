/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.raft.processor;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.entity.GetResponse;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.core.distributed.raft.JRaftServer;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.core.distributed.raft.exception.NoLeaderException;
import com.alibaba.nacos.core.distributed.raft.exception.NoSuchRaftGroupException;
import com.alibaba.nacos.core.distributed.raft.utils.BytesHolder;
import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosAsyncProcessor extends AsyncUserProcessor<BytesHolder> {

    private static final String INTEREST_NAME = BytesHolder.class.getName();

    private final JRaftServer server;
    private final int failoverRetries;

    public NacosAsyncProcessor(JRaftServer server, final int failoverRetries) {
        this.server = server;
        this.failoverRetries = failoverRetries;
    }

    @Override
    public void handleRequest(BizContext bizContext, AsyncContext asyncCtx, BytesHolder holder) {
        try {
            Log log = Log.parseFrom(holder.getBytes());
            final JRaftServer.RaftGroupTuple tuple = server.findTupleByGroup(log.getGroup());
            if (Objects.isNull(tuple)) {
                asyncCtx.sendResponse(RestResultUtils.failedWithException(new NoSuchRaftGroupException(
                        "Could not find the corresponding Raft Group : " + log.getGroup())));
                return;
            }
            if (tuple.getNode().isLeader()) {
                int retryCnt = Integer.parseInt(log.getExtendInfoOrDefault(
                        RaftSysConstants.REQUEST_FAILOVER_RETRIES, String.valueOf(failoverRetries)));
                CompletableFuture<Object> future = new CompletableFuture<>();
                server.commit(log, future, retryCnt).whenComplete((result, t) -> {
                    if (Objects.nonNull(t)) {
                        asyncCtx.sendResponse(RestResultUtils.failedWithException(t));
                        return;
                    }
                    if (result instanceof LogFuture) {
                        LogFuture f = (LogFuture) result;
                        RestResult r = null;
                        if (f.isOk()) {
                            r = RestResultUtils.success(f.getResponse());
                        } else {
                            r = RestResultUtils.success(f.getError());
                        }
                        asyncCtx.sendResponse(r);
                        return;
                    }
                    if (result instanceof GetResponse) {
                        GetResponse response = (GetResponse) result;
                        RestResult r = null;
                        if (StringUtils.isNotBlank(response.getErrMsg())) {
                            r = RestResultUtils.failedWithException(new ConsistencyException(response.getErrMsg()));
                        } else {
                            r = RestResultUtils.success(response.toByteArray());
                        }
                        asyncCtx.sendResponse(r);
                    }
                });
            }
            else {
                asyncCtx.sendResponse(RestResultUtils.failedWithException(new NoLeaderException(log.getGroup())));
            }
        } catch (Exception e) {
            asyncCtx.sendResponse(RestResultUtils.failedWithException(e));
        }
    }

    @Override
    public String interest() {
        return INTEREST_NAME;
    }



}
