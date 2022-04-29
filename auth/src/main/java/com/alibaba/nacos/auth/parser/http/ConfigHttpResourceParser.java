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

import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;

/**
 * Config Http resource parser.
 *
 * @author xiweng.yy
 */
public class ConfigHttpResourceParser extends AbstractHttpResourceParser {
    
    @Override
    protected String getNamespaceId(HttpServletRequest request) {
        return NamespaceUtil.processNamespaceParameter(request.getParameter("tenant"));
        
    }
    
    @Override
    protected String getGroup(HttpServletRequest request) {
        String groupName = request.getParameter(com.alibaba.nacos.api.common.Constants.GROUP);
        return StringUtils.isBlank(groupName) ? StringUtils.EMPTY : groupName;
    }
    
    @Override
    protected String getResourceName(HttpServletRequest request) {
        String dataId = request.getParameter(com.alibaba.nacos.api.common.Constants.DATAID);
        return StringUtils.isBlank(dataId) ? StringUtils.EMPTY : dataId;
    }
    
    @Override
    protected Properties getProperties(HttpServletRequest request) {
        return new Properties();
    }
}
