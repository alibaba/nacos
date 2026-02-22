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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelBindForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Noop implementation of Prompt handler.
 * Used when AI module is not enabled or both `naming` and `config` modules are not available.
 *
 * @author nacos
 */
@Service
@ConditionalOnMissingBean(value = PromptHandler.class, ignored = PromptNoopHandler.class)
public class PromptNoopHandler implements PromptHandler {
    
    private static final String PROMPT_NOT_ENABLED_MESSAGE = 
            "Nacos AI Prompt module and API required both `naming` and `config` module.";
    
    @Override
    public boolean publishPrompt(PromptPublishForm form, String srcUser, String srcIp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public PromptMetaInfo getPromptMeta(PromptForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public PromptVersionInfo queryPromptDetail(PromptQueryForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public boolean bindLabel(PromptLabelBindForm form, String srcUser, String srcIp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public boolean unbindLabel(PromptLabelForm form, String srcUser, String srcIp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public boolean updatePromptMetadata(PromptMetadataForm form, String srcUser, String srcIp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                PROMPT_NOT_ENABLED_MESSAGE);
    }
}
