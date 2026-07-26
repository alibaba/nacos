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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.util.Collections;
import java.util.List;

/**
 * Copilot HTTP parameter extractor.
 *
 * @author nacos
 */
public class CopilotHttpParamExtractor extends AbstractHttpParamExtractor {
    
    private static final String HTTP_METHOD_POST = "POST";
    
    private static final String SKILL_JSON_KEY = "\"skill\"";
    
    @Override
    public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
        ParamInfo paramInfo = new ParamInfo();
        
        // Try to extract skill name from request body for optimization requests
        if (HTTP_METHOD_POST.equalsIgnoreCase(request.getMethod())) {
            try {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = request.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }
                
                if (body.length() > 0) {
                    // Parse JSON body to extract skill name
                    String bodyStr = body.toString();
                    if (bodyStr.contains(SKILL_JSON_KEY)) {
                        // Extract skill from request body
                        try {
                            java.util.Map<String, Object> bodyMap =
                                JacksonUtils.toObj(bodyStr, java.util.Map.class);
                            java.util.Map<String, Object> skillMap =
                                (java.util.Map<String, Object>) bodyMap.get("skill");
                            if (skillMap != null) {
                                Skill skill =
                                    JacksonUtils.toObj(JacksonUtils.toJson(skillMap), Skill.class);
                                if (skill != null && StringUtils.isNotBlank(skill.getName())) {
                                    paramInfo.setAgentName(skill.getName());
                                    paramInfo.setNamespaceId(skill.getNamespaceId());
                                }
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }
        
        // Fallback to query parameters
        if (StringUtils.isBlank(paramInfo.getAgentName())) {
            paramInfo.setAgentName(request.getParameter("skillName"));
        }
        if (StringUtils.isBlank(paramInfo.getNamespaceId())) {
            paramInfo.setNamespaceId(request.getParameter("namespaceId"));
        }
        
        return Collections.singletonList(paramInfo);
    }
}
