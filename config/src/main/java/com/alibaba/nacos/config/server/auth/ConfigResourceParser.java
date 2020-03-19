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
package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.core.auth.Resource;
import com.alibaba.nacos.core.auth.ResourceParser;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Config resource parser
 *
 * @author nkorange
 * @since 1.2.0
 */
public class ConfigResourceParser implements ResourceParser {

    private static final String AUTH_CONFIG_PREFIX = "config/";

    @Override
    public String parseName(Object request) {
        HttpServletRequest req = (HttpServletRequest) request;
        String namespaceId = req.getParameter("tenant");
        String groupName = req.getParameter("group");
        String dataId = req.getParameter("dataId");

        StringBuilder sb = new StringBuilder();

        if (StringUtils.isNotBlank(namespaceId)) {
            sb.append(namespaceId);
        }

        sb.append(Resource.SPLITTER);

        if (StringUtils.isBlank(dataId)) {
            sb.append("*")
                .append(Resource.SPLITTER)
                .append(AUTH_CONFIG_PREFIX)
                .append("*");
        } else {
            sb.append(groupName)
                .append(Resource.SPLITTER)
                .append(AUTH_CONFIG_PREFIX)
                .append(dataId);
        }

        return sb.toString();
    }
}
