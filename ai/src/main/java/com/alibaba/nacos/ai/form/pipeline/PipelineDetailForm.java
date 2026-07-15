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

package com.alibaba.nacos.ai.form.pipeline;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serial;

/**
 * Pipeline execution detail query form for {@code GET .../detail}.
 *
 * @author nacos
 */
public class PipelineDetailForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String pipelineId;
    
    @Override
    public void validate() throws NacosApiException {
        if (StringUtils.isBlank(pipelineId)) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Required parameter 'pipelineId' is missing");
        }
    }
    
    public String getPipelineId() {
        return pipelineId;
    }
    
    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }
}
