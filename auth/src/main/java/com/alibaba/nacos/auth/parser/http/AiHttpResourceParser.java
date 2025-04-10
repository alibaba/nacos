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

package com.alibaba.nacos.auth.parser.http;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Properties;

/**
 * Config Http resource parser.
 *
 * @author xiweng.yy
 */
public class AiHttpResourceParser extends AbstractHttpResourceParser {
    
    @Override
    protected String getNamespaceId(HttpServletRequest request) {
        String namespaceId = request.getParameter(Constants.NAMESPACE_ID);
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        return namespaceId;
    }
    
    @Override
    protected String getGroup(HttpServletRequest request) {
        return Constants.DEFAULT_GROUP;
    }
    
    @Override
    protected String getResourceName(HttpServletRequest request) {
        String mcpName = request.getParameter("mcpName");
        return StringUtils.isBlank(mcpName) ? StringUtils.EMPTY : mcpName;
    }
    
    @Override
    protected Properties getProperties(HttpServletRequest request) {
        return new Properties();
    }
}
