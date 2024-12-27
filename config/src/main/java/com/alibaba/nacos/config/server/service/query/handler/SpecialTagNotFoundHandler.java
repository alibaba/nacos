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

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE_UTF8;

/**
 * SpecialTagNotFound Handler.
 * This class represents special tag not found handler in the configuration query processing chain.
 *
 * @author Nacos
 */
public class SpecialTagNotFoundHandler extends AbstractConfigQueryHandler {
    
    private static final String SPECIAL_TAG_NOT_FOUND_HANDLER = "specialTagNotFoundHandler";
    
    @Override
    public String getName() {
        return SPECIAL_TAG_NOT_FOUND_HANDLER;
    }
    
    @Override
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        if (StringUtils.isNotBlank(request.getTag())) {
            ConfigQueryChainResponse response = new ConfigQueryChainResponse();
            
            String dataId = request.getDataId();
            String group = request.getGroup();
            String tenant = request.getTenant();
            
            CacheItem cacheItem = ConfigChainEntryHandler.getThreadLocalCacheItem();
            String md5 = cacheItem.getConfigCache().getMd5(ENCODE_UTF8);
            long lastModified = cacheItem.getConfigCache().getLastModifiedTs();
            String encryptedDataKey = cacheItem.getConfigCache().getEncryptedDataKey();
            String contentType = cacheItem.getType();
            String content = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
            
            response.setContent(content);
            response.setMd5(md5);
            response.setLastModified(lastModified);
            response.setEncryptedDataKey(encryptedDataKey);
            response.setContentType(contentType);
            response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND);
            
            return response;
        } else {
            return nextHandler.handle(request);
        }
    }
}