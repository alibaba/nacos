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

package com.alibaba.nacos.core.distributed.raft.jraft;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosAsyncProcessor extends AsyncUserProcessor<Log> {

    private static final String INTEREST_NAME = NLog.class.getName();

    private final JRaftServer server;

    public NacosAsyncProcessor(JRaftServer server) {
        this.server = server;
    }

    @Override
    public void handleRequest(BizContext bizContext, AsyncContext asyncCtx, Log log) {
        final JRaftServer.RaftGroupTuple tuple = server.findNodeByLog(log);
        if (tuple.getNode().isLeader()) {
            server.commit(log).whenComplete((result, t) -> {
                if (t == null) {
                    asyncCtx.sendResponse(ResResultUtils.success());
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
