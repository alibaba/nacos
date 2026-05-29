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
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Noop implementation of Prompt handler. Used when AI module is not enabled.
 *
 * @author nacos
 */
@Service
@ConditionalOnMissingBean(value = PromptHandler.class, ignored = PromptNoopHandler.class)
public class PromptNoopHandler implements PromptHandler {
    
    private static final String PROMPT_NOT_ENABLED_MESSAGE =
        "Nacos AI Prompt module and API required both `naming` and `config` module.";
    
    private NacosApiException notImplemented() {
        return new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            PROMPT_NOT_ENABLED_MESSAGE);
    }
    
    // ========== Common APIs ==========
    
    @Override
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(PromptListForm form) throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(PromptHistoryForm form)
        throws NacosException {
        throw notImplemented();
    }
    
    // ========== Lifecycle APIs ==========
    
    @Override
    public PromptMetaInfo getPromptGovernanceDetail(String namespaceId, String promptKey)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public PromptVersionInfo getVersionDetail(String namespaceId, String promptKey, String version)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey,
        String version)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, List<PromptVariable> variables, String commitMsg, String description,
        String bizTags)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void updateDraft(String namespaceId, String promptKey, String template,
        List<PromptVariable> variables,
        String commitMsg) throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void deleteDraft(String namespaceId, String promptKey) throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public String submit(String namespaceId, String promptKey, String version)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void publish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void forcePublish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void redraft(String namespaceId, String promptKey, String version)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String promptKey, String version,
        boolean online)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void updateLabels(String namespaceId, String promptKey, Map<String, String> labels)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException {
        throw notImplemented();
    }
    
    @Override
    public void updateBizTags(String namespaceId, String promptKey, String bizTags)
        throws NacosException {
        throw notImplemented();
    }
}
