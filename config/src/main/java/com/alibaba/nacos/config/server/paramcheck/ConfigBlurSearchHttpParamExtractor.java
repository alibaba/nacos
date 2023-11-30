/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.paramcheck;

import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * The type Config blur search http param extractor.
 *
 * @author zhuoguang
 */
public class ConfigBlurSearchHttpParamExtractor extends AbstractHttpParamExtractor {
    
    private static final String BLUR_SEARCH_MODE = "blur";
    
    @Override
    public List<ParamInfo> extractParam(HttpServletRequest request) {
        String searchMode = request.getParameter("search");
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        if (StringUtils.equals(searchMode, BLUR_SEARCH_MODE)) {
            return paramInfos;
        }
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId(request.getParameter("tenant"));
        paramInfo.setDataId(request.getParameter("dataId"));
        paramInfo.setGroup(request.getParameter("group"));
        paramInfos.add(paramInfo);
        return paramInfos;
    }
}
