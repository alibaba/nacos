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

import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecBizTagsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Noop implementation of AgentSpec handler.
 * Used when AI module is not enabled or both `naming` and `config` modules are not available.
 *
 * @author nacos
 */
@Service
@ConditionalOnMissingBean(value = AgentSpecHandler.class, ignored = AgentSpecNoopHandler.class)
public class AgentSpecNoopHandler implements AgentSpecHandler {
    
    private static final String AGENTSPEC_NOT_ENABLED_MESSAGE =
        "Nacos AI AgentSpec module and API required both `naming` and `config` module.";
    
    @Override
    public AgentSpecMeta getAgentSpec(AgentSpecForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public AgentSpec getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void deleteAgentSpec(AgentSpecForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Page<AgentSpecSummary> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public String createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateDraft(AgentSpecUpdateForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void deleteDraft(AgentSpecForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public String submit(AgentSpecSubmitForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void publish(AgentSpecPublishForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void forcePublish(AgentSpecPublishForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void redraft(AgentSpecPublishForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void changeOnlineStatus(AgentSpecOnlineForm form, boolean online) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateScope(AgentSpecScopeForm form) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            AGENTSPEC_NOT_ENABLED_MESSAGE);
    }
}
