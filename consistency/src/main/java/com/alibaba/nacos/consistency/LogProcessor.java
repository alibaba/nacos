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

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Can be discovered through SPI or Spring,
 * This interface is just a function definition interface. Different consistency protocols
 * have their own LogDispatcher. It is not recommended to directly implement this interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class LogProcessor {

    protected ConsistencyProtocol<? extends Config> protocol;

    /**
     * Pass the consistency protocol implementer to LogProcessor
     *
     * @param protocol {@link ConsistencyProtocol<? extends Config>} Consistent protocol implementers
     */
    public final void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
        this.protocol = protocol;
        afterInject(protocol);
    }

    /**
     * Something is done after the ConsistencyProtocol injection
     *
     * @param protocol {@link ConsistencyProtocol<? extends Config>} Consistent protocol implementers
     */
    protected void afterInject(ConsistencyProtocol<? extends Config> protocol) {
    }

    /**
     * get data by key
     *
     * @param request request {@link GetRequest}
     * @return target type data
     */
    public abstract <D> GetResponse<D> getData(GetRequest request);

    /**
     * Commit transaction and auto inject biz info
     *
     * @param log {@link Log}
     * @return is success
     * @throws Exception
     */
    public final LogFuture commitAutoSetGroup(Log log) throws Exception {
        log.setGroup(group());
        return this.protocol.submit(log);
    }

    /**
     * Commit transaction, asynchronous and auto inject biz info
     *
     * @param log {@link Log}
     * @return {@link CompletableFuture<Boolean>}
     */
    public final CompletableFuture<LogFuture> commitAsyncAutoSetGroup(Log log) {
        log.setGroup(group());
        return this.protocol.submitAsync(log);
    }

    /**
     * Process Submitted Log
     *
     * @param log {@link Log}
     * @return {@link boolean}
     */
    public abstract LogFuture onApply(Log log);

    /**
     * Irremediable errors that need to trigger business price cuts
     *
     * @param error {@link Throwable}
     */
    public void onError(Throwable error) {
    }

    /**
     * In order for the state machine that handles the transaction to be able to route
     * the Log to the correct LogProcessor, the LogProcessor needs to have an identity
     * information
     *
     * @return Business unique identification name
     */
    public abstract String group();

}
