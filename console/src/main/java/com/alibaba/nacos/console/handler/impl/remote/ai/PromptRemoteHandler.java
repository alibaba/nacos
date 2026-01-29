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

import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptHistoryItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.ai.PromptHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Remote implementation of Prompt handler.
 * 
 * <p>Calls remote Nacos server through maintainer client for Prompt operations.</p>
 *
 * @author nacos
 */
@Service
@EnabledRemoteHandler
@Conditional(ConditionFunctionEnabled.ConditionAiEnabled.class)
public class PromptRemoteHandler implements PromptHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public PromptRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public boolean publishPrompt(PromptPublishForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().publishPrompt(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getVersion(),
                form.getTemplate(),
                form.getCommitMsg(),
                form.getDescription()
        );
    }
    
    @Override
    public PromptDetail getPrompt(PromptForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().getPromptDetail(
                form.getNamespaceId(),
                form.getPromptKey()
        );
    }
    
    @Override
    public boolean deletePrompt(PromptForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().deletePrompt(
                form.getNamespaceId(),
                form.getPromptKey()
        );
    }
    
    @Override
    public Page<PromptBasicInfo> listPrompts(PromptListForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().listPrompts(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getSearch(),
                form.getPageNo(),
                form.getPageSize()
        );
    }
    
    @Override
    public Page<PromptHistoryItem> listPromptHistory(PromptHistoryForm form) throws NacosException {
        return clientHolder.getAiMaintainerService().listPromptHistory(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getPageNo(),
                form.getPageSize()
        );
    }
    
    @Override
    public PromptDetail getPromptHistoryDetail(PromptForm form, Long historyId) throws NacosException {
        return clientHolder.getAiMaintainerService().getPromptHistoryDetail(
                form.getNamespaceId(),
                form.getPromptKey(),
                historyId
        );
    }
    
    @Override
    public boolean updatePromptMetadata(PromptMetadataForm form, String srcUser, String srcIp) throws NacosException {
        return clientHolder.getAiMaintainerService().updatePromptMetadata(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getDescription()
        );
    }
}
