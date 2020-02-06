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
import com.alibaba.nacos.core.distributed.Log;
import com.alibaba.nacos.core.distributed.NLog;
import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;

import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosAsyncProcessor extends AsyncUserProcessor<Log> {

    private static final String INTEREST_NAME = NLog.class.getName();

    private JRaftProtocol protocol;

    public NacosAsyncProcessor(JRaftProtocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public void handleRequest(BizContext bizContext, AsyncContext asyncCtx, Log log) {
        boolean isLeader = (boolean) protocol.protocolMetaData().get("leader");
        try {
            if (isLeader) {
                CompletableFuture<ResResult<Boolean>> future = protocol
                        .submitAsync(log);
                asyncCtx.sendResponse(
                        ResResult.builder().withData(future.get().getData()).build());
            }
        } catch (Exception e) {
            asyncCtx.sendResponse(
                    ResResult.builder().withData(false).withErrMsg(e.getMessage()).build());
        }
    }

    @Override
    public String interest() {
        return INTEREST_NAME;
    }
}
