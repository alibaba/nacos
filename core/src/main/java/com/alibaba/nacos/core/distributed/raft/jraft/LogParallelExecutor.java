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

import com.alibaba.nacos.core.distributed.Log;
import com.alibaba.nacos.core.distributed.LogDispatcher;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
class LogParallelExecutor {

    private final Map<String, Executor> executorMap = new HashMap<>();

    CompletableFuture<Boolean> execute(final LogDispatcher dispatcher, final Log log, final NacosClosure closure) {
        final String bizInfo = dispatcher.bizInfo();
        if (!executorMap.containsKey(bizInfo)) {
            executorMap.put(bizInfo, ExecutorFactory.
                    newSingleExecutorService("LogDispatcher-" + bizInfo));
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        final Executor executor = executorMap.get(bizInfo);
        executor.execute(() -> {
            Status status = Status.OK();
            Throwable throwable = null;
            try {
                future.complete(dispatcher.onApply(log).getData());
            } catch (Exception e) {
                throwable = e;
                status = new Status(RaftError.UNKNOWN,
                        "Exception handling within a transaction : %s",
                        e);
                Loggers.RAFT.error("LogDispatcher when onApply has some error",
                        e);
                future.completeExceptionally(e);
            }
            if (Objects.nonNull(closure)) {
                closure.setThrowable(throwable);
                closure.run(status);
            }
        });

        return future;
    }

}
