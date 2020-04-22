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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.core.distributed.raft.exception.NoLeaderException;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Status;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Closure with internal retry mechanism
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class FailoverClosureImpl<T> implements FailoverClosure<T> {

    private final CompletableFuture<T> future;
    private final int retriesLeft;
    private final RetryRunner retryRunner;
    private volatile T data;
    private volatile Throwable throwable;

    public FailoverClosureImpl(final CompletableFuture<T> future,
                               final int retriesLeft,
                               final RetryRunner retryRunner) {
        this.future = future;
        this.retriesLeft = retriesLeft;
        this.retryRunner = retryRunner;
    }

    @Override
    public void setData(T data) {
        this.data = data;
    }

    @Override
    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public void run(Status status) {
        if (status.isOk()) {
            future.complete(data);
            return;
        }
        final Throwable throwable = this.throwable;
        if (retriesLeft >= 0 && canRetryException(throwable)) {
            Loggers.RAFT.warn("[Failover] status: {}, error: {}, [{}] retries left.", status,
                    throwable, this.retriesLeft);
            this.retryRunner.run();
        } else {
            if (this.retriesLeft <= 0) {
                Loggers.RAFT.error("[InvalidEpoch-Failover] status: {}, error: {}, {} retries left.",
                        status, throwable,
                        this.retriesLeft);
            }
            if (Objects.nonNull(throwable)) {
                future.completeExceptionally(new ConsistencyException(throwable));
            } else {
                future.completeExceptionally(new ConsistencyException("Maximum number of retries has been reached"));
            }
        }
    }

    protected boolean canRetryException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof NoLeaderException) {
            return true;
        }
        return throwable instanceof IOException;
    }
}
