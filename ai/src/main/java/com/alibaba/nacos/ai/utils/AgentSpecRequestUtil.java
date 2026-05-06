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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDetailForm;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * AgentSpec request util.
 *
 * @author nacos
 */
public class AgentSpecRequestUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSpecRequestUtil.class);
    
    /**
     * Parse AgentSpec request form to {@link AgentSpec}.
     *
     * @param detailForm agentspec detail form.
     * @return agentSpec
     * @throws NacosApiException if parse failed or request parameter is conflicted.
     */
    public static AgentSpec parseAgentSpec(AgentSpecDetailForm detailForm) throws NacosApiException {
        try {
            AgentSpec result = JacksonUtils.toObj(detailForm.getAgentSpecCard(), new TypeReference<>() {
            });
            validateAgentSpec(result);
            return result;
        } catch (NacosDeserializationException e) {
            LOGGER.error(String.format("Deserialize %s from %s failed, ", AgentSpec.class.getSimpleName(),
                    detailForm.getAgentSpecCard()), e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "agentSpecCard is invalid. Can't be parsed.");
        }
    }
    
    /**
     * Validate agentSpec is legal.
     *
     * @param agentSpec agentSpec
     * @throws NacosApiException if agentSpec is illegal.
     */
    public static void validateAgentSpec(AgentSpec agentSpec) throws NacosApiException {
        validateAgentSpecField("name", agentSpec.getName());
    }
    
    private static void validateAgentSpecField(String fieldName, String fieldValue) throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentSpecCard." + fieldName + "` not present");
        }
    }
    
    /**
     * Validate uploaded agentspec zip file and extract bytes.
     *
     * @param file the uploaded multipart file
     * @return the file bytes
     * @throws NacosException if validation fails or file reading fails
     */
    public static byte[] validateAndExtractZipBytes(MultipartFile file) throws NacosException {
        if (file == null || file.isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.DATA_EMPTY, "File is required");
        }
        if (file.getSize() > Constants.AgentSpecs.MAX_UPLOAD_ZIP_BYTES) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "AgentSpec zip size must not exceed " + (Constants.AgentSpecs.MAX_UPLOAD_ZIP_BYTES / 1024 / 1024)
                            + "MB, current: " + (file.getSize() / 1024 / 1024) + "MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.PARSING_DATA_FAILED,
                    "Failed to read file: " + e.getMessage());
        }
    }
}
