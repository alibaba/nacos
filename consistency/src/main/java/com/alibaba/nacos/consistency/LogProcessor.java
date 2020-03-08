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
public interface LogProcessor {

    /**
     * Pass the consistency protocol implementer to LogProcessor
     *
     * @param protocol {@link ConsistencyProtocol<? extends Config>} Consistent protocol implementers
     */
    void injectProtocol(ConsistencyProtocol<? extends Config> protocol);

    /**
     * Returns the Protocol implementation held by this LogProcessor
     *
     * @return {@link ConsistencyProtocol<? extends Config>}
     */
    ConsistencyProtocol<? extends Config> getProtocol();

    /**
     * get data by key
     *
     * @param request request {@link GetRequest}
     * @return target type data
     */
    <D> GetResponse<D> getData(GetRequest request);

    /**
     * Commit transaction and auto inject biz info
     *
     * @param log {@link Log}
     * @return is success
     * @throws Exception
     */
    default boolean commitAutoSetBiz(Log log) throws Exception {
        log.setBiz(bizInfo());
        return getProtocol().submit(log);
    }

    /**
     * Commit transaction, asynchronous and auto inject biz info
     *
     * @param log {@link Log}
     * @return {@link CompletableFuture<Boolean>}
     */
    default CompletableFuture<Boolean> commitAsyncAutoSetBiz(Log log) {
        log.setBiz(bizInfo());
        return getProtocol().submitAsync(log);
    }

    /**
     * Process Submitted Log
     *
     * @param log {@link Log}
     * @return {@link boolean}
     */
    boolean onApply(Log log);

    /**
     * Callback triggered when a state machine error occurs
     *
     * @param throwable {@link Throwable}
     */
    default void onError(Throwable throwable) {
        throw new RuntimeException(throwable);
    }

    /**
     * this BizProcessor which interest biz
     *
     * @return biz name
     */
    String bizInfo();

    /**
     * Determine whether the key is within the business you are following
     *
     * @param key datum-key
     * @return interest result
     */
    default boolean interest(String key) {
        return key != null && key.startsWith(bizInfo());
    }

}
