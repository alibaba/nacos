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

import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromptMetaCacheInvalidateServiceTest {
    
    @Mock
    private PromptClientOperationService promptClientOperationService;
    
    private PromptMetaCacheInvalidateService service;
    
    @BeforeEach
    void setUp() {
        service = new PromptMetaCacheInvalidateService(promptClientOperationService);
    }
    
    @Test
    void onEventShouldIgnoreWhenGroupNotPromptGroup() {
        LocalDataChangeEvent event = new LocalDataChangeEvent(
                GroupKey.getKey("p1.label-version-mapping.json", "other-group", "public"));
        service.onEvent(event);
        verify(promptClientOperationService, never()).invalidateMetaCache("public", "p1");
    }
    
    @Test
    void onEventShouldIgnoreWhenDataIdNotMeta() {
        LocalDataChangeEvent event = new LocalDataChangeEvent(GroupKey.getKey("p1.admin-info.json", "nacos-ai-prompt", "public"));
        service.onEvent(event);
        verify(promptClientOperationService, never()).invalidateMetaCache("public", "p1");
    }
    
    @Test
    void onEventShouldInvalidateCacheWhenMetaChanged() {
        LocalDataChangeEvent event = new LocalDataChangeEvent(
                GroupKey.getKey("p1.label-version-mapping.json", "nacos-ai-prompt", "public"));
        service.onEvent(event);
        verify(promptClientOperationService).invalidateMetaCache("public", "p1");
    }
}
