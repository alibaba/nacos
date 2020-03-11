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

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Has nothing to do with the specific implementation of the consistency protocol
 * Initialization sequence： init(Config) => loadLogProcessor(List)
 *
 * <ul>
 *     <li>{@link Config} : Relevant configuration information required by the consistency protocol,
 *     for example, the Raft protocol needs to set the election timeout time, the location where
 *     the Log is stored, and the snapshot task execution interval</li>
 *
 *     <li>{@link LogProcessor} : The consistency protocol provides services for all businesses,
 *     but each business only cares about the transaction information belonging to that business,
 *     and the transaction processing between the various services should not block each other. Therefore,
 *     the LogProcessor is abstracted to implement the parallel processing of transactions of different services.
 *     Corresponding LogProcessor sub-interface: LogProcessor4AP or LogProcessor4CP, different consistency
 *     protocols will actively discover the corresponding LogProcessor</li>
 *
 *     <li>{@link ConsistencyProtocol#protocolMetaData()} : Returns metadata information of the consistency
 *     protocol, such as leader, term, and other metadata information in the Raft protocol</li>
 * </ul>
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
     * Copy of metadata information for this consensus protocol
     * 该一致性协议的元数据信息
     *
     * @return metaData copy
     */
    ProtocolMetaData protocolMetaData();

    /**
     * Get the value of the corresponding metadata information according to the key
     * 根据 key 获取元数据信息中的某个值
     *
     * @param key    key
     * @param subKey if value is key-value struct
     * @param <R>    target type
     * @return value
     */
    <R> R metaData(String key, String... subKey);

    /**
     * Obtain data according to the request
     *
     * @param request request
     * @return data {@link GetRequest}
     * @throws Exception
     */
    <D> GetResponse<D> getData(GetRequest request) throws Exception;

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
     * @return {@link CompletableFuture<Boolean>} submit result
     * @throws Exception when submit throw Exception
     */
    CompletableFuture<Boolean> submitAsync(Log data);

    /**
     * Bulk submission of data
     *
     * @param datums {@link Map<String, List< Log >> },
     *               The value of key is guaranteed to be the return value of {@link LogProcessor#bizInfo()}
     * @return As long as one of the processing fails, an error is returned,
     * but those that have been processed successfully will not be rolled back,
     * and the business party will guarantee the idempotence by itself
     */
    default boolean batchSubmit(Map<String, List<Log>> datums) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    /**
     * Operation and maintenance interface of consistent protocol
     *
     * @param argv command
     * @return {@link RestResult <String>}
     */
    default RestResult<String> maintenance(String[] argv) {
        return RestResult.<String>builder().build();
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
