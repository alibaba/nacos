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

package com.alibaba.nacos.config.server.service.query;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.utils.RequestUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;

/**
 * DefaultChainRequestExtractor.
 *
 * @author Nacos
 */
public class DefaultChainRequestExtractor implements ConfigQueryChainRequestExtractor {
    
    @Override
    public String getName() {
        return "nacos";
    }
    
    @Override
    public ConfigQueryChainRequest extract(HttpServletRequest request) {
        final String dataId = request.getParameter("dataId");
        final String group = request.getParameter("group");
        String tenant = request.getParameter("tenant");
        if (StringUtils.isBlank(tenant)) {
            tenant = StringUtils.EMPTY;
        }
        String tag = request.getParameter("tag");
        String autoTag = request.getHeader(VIPSERVER_TAG);
        String clientIp = RequestUtil.getRemoteIp(request);
        
        Map<String, String> appLabels = new HashMap<>(4);
        appLabels.put(BetaGrayRule.CLIENT_IP_LABEL, clientIp);
        if (StringUtils.isNotBlank(tag)) {
            appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, tag);
        } else if (StringUtils.isNotBlank(autoTag)) {
            appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, autoTag);
        }
        
        ConfigQueryChainRequest chainRequest = new ConfigQueryChainRequest();
        chainRequest.setDataId(dataId);
        chainRequest.setGroup(group);
        chainRequest.setTenant(tenant);
        chainRequest.setTag(tag);
        chainRequest.setAppLabels(appLabels);
        
        return chainRequest;
    }
    
    @Override
    public ConfigQueryChainRequest extract(ConfigQueryRequest request, RequestMeta requestMeta) {
        ConfigQueryChainRequest chainRequest = new ConfigQueryChainRequest();
        
        String tag = request.getTag();
        Map<String, String> appLabels = new HashMap<>(4);
        appLabels.put(BetaGrayRule.CLIENT_IP_LABEL, requestMeta.getClientIp());
        if (StringUtils.isNotBlank(tag)) {
            appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, tag);
        } else {
            appLabels.putAll(requestMeta.getAppLabels());
        }
        
        chainRequest.setDataId(request.getDataId());
        chainRequest.setGroup(request.getGroup());
        chainRequest.setTenant(request.getTenant());
        chainRequest.setTag(request.getTag());
        chainRequest.setAppLabels(appLabels);
        
        return chainRequest;
    }
}