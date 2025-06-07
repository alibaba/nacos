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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;

import java.io.IOException;

/**
 * Formal Handler. This class represents a formal handler in the configuration query processing chain. If the request
 * has not been processed by previous handlers, it will be handled by this handler.
 *
 * @author Nacos
 */
public class FormalHandler extends AbstractConfigQueryHandler {
    
    private static final String FORMAL_HANDLER = "formalHandler";
    
    @Override
    public String getName() {
        return FORMAL_HANDLER;
    }
    
    @Override
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        
        String dataId = request.getDataId();
        String group = request.getGroup();
        String tenant = request.getTenant();
        
        CacheItem cacheItem = ConfigChainEntryHandler.getThreadLocalCacheItem();
        String md5 = cacheItem.getConfigCache().getMd5();
        String content = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
        if (StringUtils.isBlank(content)) {
            response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
            return response;
        }
        long lastModified = cacheItem.getConfigCache().getLastModifiedTs();
        String encryptedDataKey = cacheItem.getConfigCache().getEncryptedDataKey();
        String configType = cacheItem.getType();
        response.setContent(content);
        response.setMd5(md5);
        response.setLastModified(lastModified);
        response.setEncryptedDataKey(encryptedDataKey);
        response.setConfigType(configType);
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        
        return response;
    }
}