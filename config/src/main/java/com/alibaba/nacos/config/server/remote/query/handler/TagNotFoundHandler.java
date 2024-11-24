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

package com.alibaba.nacos.config.server.remote.query.handler;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.model.ConfigQueryChainResponse;

import java.io.IOException;

/**
 * TagNotFoundHandler Handler.
 * This class represents tag not found handler in the configuration query processing chain.
 *
 * @author Nacos
 */
public class TagNotFoundHandler extends AbstractConfigQueryHandler {
    
    private static final String TAG_NOT_FOUND_HANDLER = "tagNoFoundHandler";
    
    @Override
    public String getQueryHandlerName() {
        return TAG_NOT_FOUND_HANDLER;
    }
    
    @Override
    public boolean canHandler(ConfigQueryChainRequest request) {
        return StringUtils.isNotBlank(request.getTag());
    }
    
    @Override
    public ConfigQueryChainResponse doHandle(ConfigQueryChainRequest request) throws IOException {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        
        String md5 = null;
        String content = null;
        long lastModified = 0L;
        String encryptedDataKey = null;
        
        response.setContent(content);
        response.setMd5(md5);
        response.setEncryptedDataKey(encryptedDataKey);
        response.setLastModified(lastModified);
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.TAG_NOT_FOUND);
        
        return response;
    }
}