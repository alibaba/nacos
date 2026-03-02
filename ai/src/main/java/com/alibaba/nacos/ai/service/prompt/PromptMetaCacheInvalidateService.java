/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.PromptDataIdUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import org.springframework.stereotype.Service;

/**
 * Prompt meta cache invalidate service.
 *
 * @author nacos
 */
@Service
public class PromptMetaCacheInvalidateService extends Subscriber<LocalDataChangeEvent> {
    
    private final PromptClientOperationService promptOperationService;
    
    public PromptMetaCacheInvalidateService(PromptClientOperationService promptOperationService) {
        this.promptOperationService = promptOperationService;
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void onEvent(LocalDataChangeEvent event) {
        String[] keyParts = GroupKey.parseKey(event.groupKey);
        String dataId = keyParts[0];
        String group = keyParts[1];
        String tenant = keyParts.length > 2 ? keyParts[2] : "";
        if (!Constants.Prompt.PROMPT_GROUP.equals(group)) {
            return;
        }
        if (!PromptDataIdUtils.isLabelVersionMappingDataId(dataId)) {
            return;
        }
        String promptKey = PromptDataIdUtils.extractPromptKeyFromLabelVersionMappingDataId(dataId);
        promptOperationService.invalidateMetaCache(NamespaceUtil.processNamespaceParameter(tenant), promptKey);
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
}
