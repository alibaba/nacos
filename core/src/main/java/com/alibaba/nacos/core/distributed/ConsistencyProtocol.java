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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.common.model.ResResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Has nothing to do with the specific implementation of the consistency protocol
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ConsistencyProtocol<T extends Config> {

    /**
     * Consistency protocol initialization: perform initialization operations based on the incoming Config
     * 一致性协议初始化，根据 Config 实现类
     *
     * @param config {@link Config}
     */
    void init(T config);

    /**
     * Metadata information for this conformance protocol
     * 该一致性协议的元数据信息
     *
     * @return {@link Map<String, Object>}
     */
    Map<String, Object> protocolMetaData();

    /**
     * Get the value of the corresponding metadata information according to the key
     * 根据 key 获取元数据信息中的某个值
     *
     * @param key key
     * @param <T> target type
     * @return value
     */
    <T> T metaData(String key);

    /**
     * register biz-processor
     * 注册业务处理器，处理不同业务的Log
     *
     * @param processor {@link LogDispatcher}
     */
    void registerBizProcessor(LogDispatcher processor);

    /**
     * Obtain data according to the key, and implement specific data acquisition by BizProcessor
     *
     * @param key key
     * @return data
     * @throws Exception
     */
    <T> T getData(String key) throws Exception;

    /**
     * Data operation, returning submission results synchronously
     * 同步数据提交，在 Datum 中已携带相应的数据操作信息
     *
     * @param data {@link Log}
     * @return submit operation result
     * @throws Exception
     */
    boolean submit(Log data) throws Exception;

    /**
     * Data submission operation, returning submission results asynchronously
     * 异步数据提交，在 Datum 中已携带相应的数据操作信息，返回一个Future，自行操作，提交发生的异常会在CompleteFuture中
     *
     * @param data {@link Log}
     * @return {@link CompletableFuture<ResResult<Boolean>>} submit result
     * @throws Exception when submit throw Exception
     */
    CompletableFuture<ResResult<Boolean>> submitAsync(Log data);

    /**
     * Bulk submission of data
     *
     * @param datums {@link Map<String, List< Log >> },
     *               The value of key is guaranteed to be the return value of {@link LogDispatcher#bizInfo()}
     * @return As long as one of the processing fails, an error is returned,
     * but those that have been processed successfully will not be rolled back,
     * and the business party will guarantee the idempotence by itself
     */
    default boolean batchSubmit(Map<String, List<Log>> datums) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    /**
     * Concerned Configuration Object
     *
     * @return which class extends {@link Config}
     */
    Class<? extends Config> configType();

    /**
     * Consistency agreement service shut down
     * 一致性协议服务关闭
     */
    void shutdown();

}
