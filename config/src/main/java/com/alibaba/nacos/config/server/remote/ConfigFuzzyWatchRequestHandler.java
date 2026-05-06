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

import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.config.server.model.event.ConfigFuzzyWatchEvent;
import com.alibaba.nacos.config.server.service.ConfigFuzzyWatchContextService;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigFuzzyWatchRequestParamsExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.utils.StringPool;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_CANCEL_WATCH;
import static com.alibaba.nacos.api.common.Constants.WATCH_TYPE_WATCH;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;

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
public class ConfigFuzzyWatchRequestHandler extends RequestHandler<ConfigFuzzyWatchRequest, ConfigFuzzyWatchResponse> {
    
    private ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    public ConfigFuzzyWatchRequestHandler(ConfigFuzzyWatchContextService configFuzzyWatchContextService) {
        this.configFuzzyWatchContextService = configFuzzyWatchContextService;
    }
    
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
    @NamespaceValidation
    @TpsControl(pointName = "ConfigFuzzyWatch")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(rpcExtractor = ConfigFuzzyWatchRequestParamsExtractor.class)
    public ConfigFuzzyWatchResponse handle(ConfigFuzzyWatchRequest request, RequestMeta meta) throws NacosException {
        String connectionId = StringPool.get(meta.getConnectionId());
        String groupKeyPattern = request.getGroupKeyPattern();
        if (WATCH_TYPE_WATCH.equals(request.getWatchType())) {
            // Add client to the fuzzy listening context
            try {
                configFuzzyWatchContextService.addFuzzyWatch(groupKeyPattern, connectionId);
                // Get existing group keys for the client and publish initialization event
                Set<String> clientExistingGroupKeys = request.getReceivedGroupKeys();
                NotifyCenter.publishEvent(
                        new ConfigFuzzyWatchEvent(connectionId, clientExistingGroupKeys, groupKeyPattern,
                                request.isInitializing()));
            } catch (NacosException nacosException) {
                ConfigFuzzyWatchResponse configFuzzyWatchResponse = new ConfigFuzzyWatchResponse();
                configFuzzyWatchResponse.setErrorInfo(nacosException.getErrCode(), nacosException.getErrMsg());
                return configFuzzyWatchResponse;
            }
            
            boolean reachToUpLimit = configFuzzyWatchContextService.reachToUpLimit(groupKeyPattern);
            if (reachToUpLimit) {
                ConfigFuzzyWatchResponse configFuzzyWatchResponse = new ConfigFuzzyWatchResponse();
                configFuzzyWatchResponse.setErrorInfo(FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(),
                        FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getMsg());
                return configFuzzyWatchResponse;
            }
            
        } else if (WATCH_TYPE_CANCEL_WATCH.equals(request.getWatchType())) {
            configFuzzyWatchContextService.removeFuzzyListen(groupKeyPattern, connectionId);
        }
        
        // Return response
        return new ConfigFuzzyWatchResponse();
    }
}
