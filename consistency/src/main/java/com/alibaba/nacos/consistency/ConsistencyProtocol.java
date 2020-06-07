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


import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.Response;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Has nothing to do with the specific implementation of the consistency protocol
 * Initialization sequence： init(Config)
 *
 * <ul>
 *     <li>{@link Config} : Relevant configuration information required by the consistency protocol,
 *     for example, the Raft protocol needs to set the election timeout time, the location where
 *     the Log is stored, and the snapshot task execution interval</li>
 *     <li>{@link ConsistencyProtocol#protocolMetaData()} : Returns metadata information of the consistency
 *     protocol, such as leader, term, and other metadata information in the Raft protocol</li>
 * </ul>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface ConsistencyProtocol<T extends Config, P extends LogProcessor> extends CommandOperations {

    /**
     * Consistency protocol initialization: perform initialization operations based on the incoming Config
     * 一致性协议初始化，根据 Config 实现类
     *
     * @param config {@link Config}
     */
    void init(T config);

    /**
     * Add a log handler
     *
     * @param processors {@link LogProcessor}
     */
    void addLogProcessors(Collection<P> processors);

    /**
     * Copy of metadata information for this consensus protocol
     * 该一致性协议的元数据信息
     *
     * @return metaData {@link ProtocolMetaData}
     */
    ProtocolMetaData protocolMetaData();

    /**
     * Obtain data according to the request
     *
     * @param request request
     * @return data {@link Response}
     * @throws Exception
     */
    Response getData(GetRequest request) throws Exception;

    /**
     * Get data asynchronously
     *
     * @param request request
     * @return data {@link CompletableFuture<Response>}
     */
    CompletableFuture<Response> aGetData(GetRequest request);

    /**
     * Data operation, returning submission results synchronously
     * 同步数据提交，在 Datum 中已携带相应的数据操作信息
     *
     * @param data {@link Log}
     * @return submit operation result {@link Response}
     * @throws Exception
     */
    Response submit(Log data) throws Exception;

    /**
     * Data submission operation, returning submission results asynchronously
     * 异步数据提交，在 Datum 中已携带相应的数据操作信息，返回一个Future，自行操作，提交发生的异常会在CompleteFuture中
     *
     * @param data {@link Log}
     * @return {@link CompletableFuture<Response>} submit result
     * @throws Exception when submit throw Exception
     */
    CompletableFuture<Response> submitAsync(Log data);

    /**
     * New member list
     * 新的成员节点列表，一致性协议自行处理相应的成员节点是加入还是离开
     *
     * @param addresses [ip:port, ip:port, ...]
     */
    void memberChange(Set<String> addresses);

    /**
     * Consistency agreement service shut down
     * 一致性协议服务关闭
     */
    void shutdown();

}
