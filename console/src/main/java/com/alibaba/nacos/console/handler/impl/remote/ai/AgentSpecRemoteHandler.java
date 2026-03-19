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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecDraftCreateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecLabelsUpdateForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecListForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecOnlineForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminDetail;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminListItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Remote implementation of AgentSpec handler.
 *
 * <p>Calls remote Nacos server through maintainer client for AgentSpec operations.</p>
 * <p>Note: Full delegation to AgentSpecMaintainerService will be wired once the maintainer SDK is implemented.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class AgentSpecRemoteHandler implements AgentSpecHandler {
    
    private static final String AGENTSPEC_REMOTE_NOT_READY_MESSAGE =
            "AgentSpec remote handler requires AgentSpecMaintainerService to be implemented.";
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public AgentSpecRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    @Override
    public AgentSpecAdminDetail getAgentSpec(AgentSpecForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().getAgentSpecDetail() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }
    
    @Override
    public void deleteAgentSpec(AgentSpecForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().deleteAgentSpec() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public Page<AgentSpecAdminListItem> listAgentSpecs(AgentSpecListForm agentSpecListForm, PageForm pageForm)
            throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().listAgentSpecs() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().uploadAgentSpecFromZip() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public String createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().createAgentSpecDraft() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public void updateDraft(AgentSpecUpdateForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().updateAgentSpecDraft() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public void deleteDraft(AgentSpecForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().deleteAgentSpecDraft() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public String submit(AgentSpecSubmitForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().submitAgentSpec() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public void publish(AgentSpecPublishForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().publishAgentSpec() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().updateAgentSpecLabels() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }

    @Override
    public void changeOnlineStatus(AgentSpecOnlineForm form, boolean online) throws NacosException {
        // TODO: delegate to clientHolder.getAiMaintainerService().changeAgentSpecOnlineStatus() once AgentSpecMaintainerService is available
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                AGENTSPEC_REMOTE_NOT_READY_MESSAGE);
    }
}
