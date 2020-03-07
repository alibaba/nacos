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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.distributed.raft.utils.JLog;
import com.alibaba.nacos.core.utils.ConvertUtils;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosAsyncProcessor extends AsyncUserProcessor<JLog> {

    private static final String INTEREST_NAME = JLog.class.getName();

    private final JRaftServer server;
    private  final int failoverRetries;

    public NacosAsyncProcessor(JRaftServer server, final int failoverRetries) {
        this.server = server;
        this.failoverRetries = failoverRetries;
    }

    @Override
    public void handleRequest(BizContext bizContext, AsyncContext asyncCtx, JLog log) {
        final JRaftServer.RaftGroupTuple tuple = server.findNodeByBiz(log.getBiz());

        if (Objects.isNull(tuple)) {
            asyncCtx.sendResponse(ResResultUtils.failed("Could not find the corresponding Raft Group : " + log.getBiz()));
            return;
        }

        if (tuple.getNode().isLeader()) {
            int retryCnt = ConvertUtils.toInt(log.extendVal(RaftSysConstants.REQUEST_FAILOVER_RETRIES), failoverRetries);
            CompletableFuture<Object> future = new CompletableFuture<>();
            server.commit(log, future, retryCnt).whenComplete((result, t) -> {
                if (t == null) {
                    asyncCtx.sendResponse(ResResultUtils.success(result));
                } else {
                    asyncCtx.sendResponse(
                            ResResult.builder()
                                    .withData(false)
                                    .withErrMsg(t.getMessage())
                                    .build());
                }
            });
        } else {
            asyncCtx.sendResponse(ResResultUtils.failed("Not leader"));
        }
    }

    @Override
    public String interest() {
        return INTEREST_NAME;
    }
}
