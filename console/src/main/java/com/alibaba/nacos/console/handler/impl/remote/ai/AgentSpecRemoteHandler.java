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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.EnabledAiHandler;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Remote implementation of AgentSpec handler.
 *
 * <p>Calls remote Nacos server through maintainer client for AgentSpec operations.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@EnabledAiHandler
public class AgentSpecRemoteHandler implements AgentSpecHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public AgentSpecRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public AgentSpecMeta getAgentSpec(AgentSpecForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec()
            .getAgentSpecAdminDetail(form.getNamespaceId(), form.getAgentSpecName());
    }
    
    @Override
    public AgentSpec getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec().getAgentSpecVersionDetail(
            form.getNamespaceId(),
            form.getAgentSpecName(),
            form.getVersion());
    }
    
    @Override
    public void deleteAgentSpec(AgentSpecForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().deleteAgentSpec(
            form.getNamespaceId(),
            form.getAgentSpecName());
    }
    
    @Override
    public Page<AgentSpecSummary> listAgentSpecs(AgentSpecListForm agentSpecListForm,
        AiResourceFilterableForm filterableForm, PageForm pageForm) throws NacosException {
        Page<AgentSpecSummary> source =
            clientHolder.getAiMaintainerService().agentSpec().listAgentSpecAdminItems(
                agentSpecListForm.getNamespaceId(), agentSpecListForm.getAgentSpecName(),
                agentSpecListForm.getSearch(), agentSpecListForm.getOrderBy(),
                filterableForm.getOwner(), filterableForm.getScope(),
                pageForm.getPageNo(), pageForm.getPageSize());
        if (source != null) {
            return source;
        }
        Page<AgentSpecSummary> empty = new Page<>();
        empty.setTotalCount(0);
        empty.setPagesAvailable(0);
        empty.setPageNumber(pageForm.getPageNo());
        empty.setPageItems(new ArrayList<>());
        return empty;
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec().uploadAgentSpecFromZip(namespaceId,
            zipBytes,
            overwrite);
    }
    
    @Override
    public String createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec()
            .createDraft(form.getNamespaceId(), form.getAgentSpecName(), form.getBasedOnVersion(),
                form.getTargetVersion());
    }
    
    @Override
    public void updateDraft(AgentSpecUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec()
            .updateDraft(form.getNamespaceId(), form.getAgentSpecCard(), form.getSetAsLatest());
    }
    
    @Override
    public void deleteDraft(AgentSpecForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec()
            .deleteDraft(form.getNamespaceId(), form.getAgentSpecName());
    }
    
    @Override
    public String submit(AgentSpecSubmitForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec()
            .submit(form.getNamespaceId(), form.getAgentSpecName(), form.getVersion());
    }
    
    @Override
    public void publish(AgentSpecPublishForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().publish(form.getNamespaceId(),
            form.getAgentSpecName(), form.getVersion(), true);
    }
    
    @Override
    public void forcePublish(AgentSpecPublishForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().forcePublish(form.getNamespaceId(),
            form.getAgentSpecName(), form.getVersion(), true);
    }
    
    @Override
    public void redraft(AgentSpecPublishForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec()
            .redraft(form.getNamespaceId(), form.getAgentSpecName(), form.getVersion());
    }
    
    @Override
    public void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().updateLabels(form.getNamespaceId(),
            form.getAgentSpecName(), form.getLabels());
    }
    
    @Override
    public void updateBizTags(AgentSpecBizTagsUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().updateBizTags(form.getNamespaceId(),
            form.getAgentSpecName(), form.getBizTags());
    }
    
    @Override
    public void changeOnlineStatus(AgentSpecOnlineForm form, boolean online) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().changeOnlineStatus(form.getNamespaceId(),
            form.getAgentSpecName(), form.getScope(), form.getVersion(), online);
    }
    
    @Override
    public void updateScope(AgentSpecScopeForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().updateScope(form.getNamespaceId(),
            form.getAgentSpecName(), form.getScope());
    }
}
