/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.parser.ResourceParser;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Properties;

/**
 * Identity-only AI capabilities have no namespace or business resource.
 *
 * @author Nacos
 */
public class AiCapabilityHttpResourceParser implements ResourceParser<HttpServletRequest> {
    
    @Override
    public Resource parse(HttpServletRequest request, Secured secured) {
        Properties properties = new Properties();
        properties.setProperty(Constants.Resource.ACTION, secured.action().toString());
        for (String tag : secured.tags()) {
            properties.setProperty(tag, tag);
        }
        return new Resource("", "", "", secured.signType(), properties);
    }
}
