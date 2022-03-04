/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.auth.context.HttpIdentityContextBuilder;
import com.alibaba.nacos.auth.parser.http.AbstractHttpResourceParser;
import com.alibaba.nacos.auth.parser.http.ConfigHttpResourceParser;
import com.alibaba.nacos.auth.parser.http.NamingHttpResourceParser;
import com.alibaba.nacos.auth.util.Loggers;
import com.alibaba.nacos.common.utils.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Auth Service for Http protocol.
 *
 * @author xiweng.yy
 */
public class HttpProtocolAuthService extends AbstractProtocolAuthService<HttpServletRequest> {
    
    private final Map<String, AbstractHttpResourceParser> resourceParserMap;
    
    private final HttpIdentityContextBuilder identityContextBuilder;
    
    public HttpProtocolAuthService(AuthConfigs authConfigs) {
        super(authConfigs);
        resourceParserMap = new HashMap<>(2);
        identityContextBuilder = new HttpIdentityContextBuilder(authConfigs);
    }
    
    @Override
    public void initialize() {
        resourceParserMap.put(SignType.NAMING, new NamingHttpResourceParser());
        resourceParserMap.put(SignType.CONFIG, new ConfigHttpResourceParser());
    }
    
    @Override
    public Resource parseResource(HttpServletRequest request, Secured secured) {
        if (StringUtils.isNotBlank(secured.resource())) {
            return parseSpecifiedResource(secured);
        }
        String type = secured.signType();
        if (!resourceParserMap.containsKey(type)) {
            Loggers.AUTH.warn("Can't find Http request resourceParser for type {} use specified resource parser", type);
            return useSpecifiedParserToParse(secured, request);
        }
        return resourceParserMap.get(type).parse(request, secured);
    }
    
    @Override
    public IdentityContext parseIdentity(HttpServletRequest request) {
        return identityContextBuilder.build(request);
    }
}
