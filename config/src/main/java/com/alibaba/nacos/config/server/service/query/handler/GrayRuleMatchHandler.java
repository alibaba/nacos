/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query.handler;

import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;

import java.io.IOException;

/**
 * GrayRuleMatchHandler. This class represents a gray rule handler in the configuration query processing chain. It
 * checks if the request matches any gray rules and processes the request accordingly.
 *
 * @author Nacos
 */
public class GrayRuleMatchHandler extends AbstractConfigQueryHandler {
    
    private static final String GRAY_RULE_MATCH_HANDLER = "grayRuleMatchHandler";
    
    @Override
    public String getName() {
        return GRAY_RULE_MATCH_HANDLER;
    }
    
    @Override
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        // Check if the request matches any gray rules
        CacheItem cacheItem = ConfigChainEntryHandler.getThreadLocalCacheItem();
        ConfigCacheGray matchedGray = null;
        if (cacheItem.getSortConfigGrays() != null && !cacheItem.getSortConfigGrays().isEmpty()) {
            for (ConfigCacheGray configCacheGray : cacheItem.getSortConfigGrays()) {
                if (configCacheGray.match(request.getAppLabels())) {
                    matchedGray = configCacheGray;
                    break;
                }
            }
        }
        
        if (matchedGray != null) {
            ConfigQueryChainResponse response = new ConfigQueryChainResponse();
            
            long lastModified = matchedGray.getLastModifiedTs();
            String md5 = matchedGray.getMd5();
            String encryptedDataKey = matchedGray.getEncryptedDataKey();
            String content = ConfigDiskServiceFactory.getInstance()
                    .getGrayContent(request.getDataId(), request.getGroup(), request.getTenant(),
                            matchedGray.getGrayName());
            
            response.setContent(content);
            response.setMd5(md5);
            response.setLastModified(lastModified);
            response.setEncryptedDataKey(encryptedDataKey);
            response.setMatchedGray(matchedGray);
            response.setConfigType(cacheItem.getType());
            response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY);
            
            return response;
        } else {
            return nextHandler.handle(request);
        }
    }
}