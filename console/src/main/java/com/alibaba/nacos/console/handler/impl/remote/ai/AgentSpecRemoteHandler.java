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
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecSubmitForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecUpdateForm;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminDetail;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminListItem;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.AgentSpecHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.core.model.form.PageForm;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote implementation of AgentSpec handler.
 *
 * <p>Calls remote Nacos server through maintainer client for AgentSpec operations.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class AgentSpecRemoteHandler implements AgentSpecHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public AgentSpecRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    @Override
    public AgentSpecAdminDetail getAgentSpec(AgentSpecForm form) throws NacosException {
        // Remote maintainer client currently does not support full admin detail;
        // return empty detail as placeholder.
        return new AgentSpecAdminDetail();
    }

    @Override
    public AgentSpec getAgentSpecVersion(AgentSpecForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec().getAgentSpecVersionDetail(
                form.getNamespaceId(),
                form.getAgentSpecName(),
                form.getVersion()
        );
    }
    
    @Override
    public void deleteAgentSpec(AgentSpecForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().deleteAgentSpec(
                form.getNamespaceId(),
                form.getAgentSpecName()
        );
    }

    @Override
    public Page<AgentSpecAdminListItem> listAgentSpecs(AgentSpecListForm agentSpecListForm, PageForm pageForm)
            throws NacosException {
        // Remote maintainer client returns Page<AgentSpecBasicInfo>; convert to Page<AgentSpecAdminListItem>
        Page<AgentSpecBasicInfo> source = clientHolder.getAiMaintainerService().agentSpec().listAgentSpecs(
                agentSpecListForm.getNamespaceId(),
                agentSpecListForm.getAgentSpecName(),
                agentSpecListForm.getSearch(),
                pageForm.getPageNo(),
                pageForm.getPageSize()
        );
        Page<AgentSpecAdminListItem> result = new Page<>();
        result.setTotalCount(source == null ? 0 : source.getTotalCount());
        result.setPagesAvailable(source == null ? 0 : source.getPagesAvailable());
        result.setPageNumber(pageForm.getPageNo());
        List<AgentSpecAdminListItem> items = new ArrayList<>();
        if (source != null && source.getPageItems() != null) {
            for (AgentSpecBasicInfo info : source.getPageItems()) {
                if (info == null) {
                    continue;
                }
                AgentSpecAdminListItem item = new AgentSpecAdminListItem();
                item.setName(info.getName());
                item.setDescription(info.getDescription());
                items.add(item);
            }
        }
        result.setPageItems(items);
        return result;
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
            throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec().uploadAgentSpecFromZip(namespaceId, zipBytes,
            overwrite);
    }

    @Override
    public String createDraft(AgentSpecDraftCreateForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().agentSpec()
            .createDraft(form.getNamespaceId(), form.getAgentSpecName(), form.getBasedOnVersion());
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
            form.getAgentSpecName(), form.getVersion(), form.getUpdateLatestLabel());
    }

    @Override
    public void updateLabels(AgentSpecLabelsUpdateForm form) throws NacosException {
        clientHolder.getAiMaintainerService().agentSpec().updateLabels(form.getNamespaceId(),
            form.getAgentSpecName(), form.getLabels());
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
