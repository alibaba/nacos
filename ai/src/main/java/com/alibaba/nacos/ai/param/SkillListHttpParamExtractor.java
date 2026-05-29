/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;

/**
 * Nacos AI Skill list param extractor.
 *
 * @author nacos
 */
public class SkillListHttpParamExtractor extends AbstractHttpParamExtractor {
    
    private static final String SKILL_NAME_PARAM = "skillName";
    
    private static final String SEARCH_PARAM = "search";
    
    @Override
    public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId(request.getParameter("namespaceId"));
        String skillName = request.getParameter(SKILL_NAME_PARAM);
        if (Constants.Skills.SEARCH_ACCURATE.equalsIgnoreCase(request.getParameter(SEARCH_PARAM))) {
            paramInfo.setSkillName(skillName);
        } else {
            paramInfo.setSkillSearchName(skillName);
        }
        return Collections.singletonList(paramInfo);
    }
}
