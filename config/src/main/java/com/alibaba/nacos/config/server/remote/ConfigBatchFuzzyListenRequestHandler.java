/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigBatchFuzzyListenRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigBatchFuzzyListenResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.GroupKeyPattern;
import com.alibaba.nacos.config.server.model.event.ConfigBatchFuzzyListenEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigBatchFuzzyListenRequestParamsExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.utils.StringPool;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for processing batch fuzzy listen requests.
 * <p>
 * This handler is responsible for processing batch fuzzy listen requests sent by clients. It adds or removes clients
 * from the fuzzy listening context based on the request, and publishes corresponding events to notify interested
 * parties.
 * </p>
 *
 * @author stone-98
 * @date 2024/3/4
 */
@Component
public class ConfigBatchFuzzyListenRequestHandler
        extends RequestHandler<ConfigBatchFuzzyListenRequest, ConfigBatchFuzzyListenResponse> {
    
    /**
     * Context for managing fuzzy listen changes.
     */
    @Autowired
    private ConfigChangeListenContext configChangeListenContext;
    
    /**
     * Handles the batch fuzzy listen request.
     * <p>
     * This method processes the batch fuzzy listen request by adding or removing clients from the fuzzy listening
     * context based on the request, and publishes corresponding events to notify interested parties.
     * </p>
     *
     * @param request The batch fuzzy listen request
     * @param meta    Request meta information
     * @return The response to the batch fuzzy listen request
     * @throws NacosException If an error occurs while processing the request
     */
    @Override
    @TpsControl(pointName = "ConfigFuzzyListen")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(rpcExtractor = ConfigBatchFuzzyListenRequestParamsExtractor.class)
    public ConfigBatchFuzzyListenResponse handle(ConfigBatchFuzzyListenRequest request, RequestMeta meta)
            throws NacosException {
        String connectionId = StringPool.get(meta.getConnectionId());
        for (ConfigBatchFuzzyListenRequest.Context context : request.getContexts()) {
            String groupKeyPattern = GroupKeyPattern.generateFuzzyListenGroupKeyPattern(context.getDataIdPattern(),
                    context.getGroup(), context.getTenant());
            groupKeyPattern = StringPool.get(groupKeyPattern);
            if (context.isListen()) {
                // Add client to the fuzzy listening context
                configChangeListenContext.addFuzzyListen(groupKeyPattern, connectionId);
                // Get existing group keys for the client and publish initialization event
                Set<String> clientExistingGroupKeys = null;
                if (CollectionUtils.isNotEmpty(context.getDataIds())) {
                    clientExistingGroupKeys = context.getDataIds().stream()
                            .map(dataId -> GroupKey.getKeyTenant(dataId, context.getGroup(), context.getTenant()))
                            .collect(Collectors.toSet());
                }
                NotifyCenter.publishEvent(
                        new ConfigBatchFuzzyListenEvent(connectionId, clientExistingGroupKeys, groupKeyPattern,
                                context.isInitializing()));
            } else {
                // Remove client from the fuzzy listening context
                configChangeListenContext.removeFuzzyListen(groupKeyPattern, connectionId);
            }
        }
        // Return response
        return new ConfigBatchFuzzyListenResponse();
    }
}
