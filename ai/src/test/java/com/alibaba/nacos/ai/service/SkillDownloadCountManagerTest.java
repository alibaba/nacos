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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.event.SkillDownloadEvent;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.common.notify.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SkillDownloadCountManager}.
 */
@ExtendWith(MockitoExtension.class)
class SkillDownloadCountManagerTest {
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private SkillDownloadCountManager manager;
    
    @BeforeEach
    void setUp() {
        manager = new SkillDownloadCountManager(aiResourcePersistService,
            aiResourceVersionPersistService);
    }
    
    @AfterEach
    void tearDown() {
        manager.shutdown();
    }
    
    @Test
    void subscribeTypesShouldOnlySubscribeSkillDownloadEvent() {
        assertEquals(SkillDownloadEvent.class, manager.subscribeTypes().get(0));
    }
    
    @Test
    void onEventShouldIgnoreOtherEventTypes() throws Exception {
        manager.onEvent(new Event() {
        });
        
        invokeFlush();
        
        verify(aiResourcePersistService, never()).incrementDownloadCount(eq("public"),
            eq("skill-a"), eq("skill"), anyLong());
        verify(aiResourceVersionPersistService, never()).incrementDownloadCount(eq("public"),
            eq("skill-a"), eq("skill"), eq("1.0.0"), anyLong());
    }
    
    @Test
    void flushShouldAggregateCountsPerSkillVersion() throws Exception {
        manager.onEvent(new SkillDownloadEvent("public", "skill-a", "skill", "1.0.0"));
        manager.onEvent(new SkillDownloadEvent("public", "skill-a", "skill", "1.0.0"));
        manager.onEvent(new SkillDownloadEvent("public", "skill-a", "skill", "2.0.0"));
        
        invokeFlush();
        
        verify(aiResourceVersionPersistService).incrementDownloadCount("public", "skill-a",
            "skill", "1.0.0", 2L);
        verify(aiResourceVersionPersistService).incrementDownloadCount("public", "skill-a",
            "skill", "2.0.0", 1L);
        verify(aiResourcePersistService).incrementDownloadCount("public", "skill-a", "skill",
            2L);
        verify(aiResourcePersistService).incrementDownloadCount("public", "skill-a", "skill",
            1L);
    }
    
    @Test
    void flushShouldPutCountBackWhenPersistFails() throws Exception {
        doThrow(new RuntimeException("db down")).when(aiResourceVersionPersistService)
            .incrementDownloadCount("public", "skill-a", "skill", "1.0.0", 1L);
        manager.onEvent(new SkillDownloadEvent("public", "skill-a", "skill", "1.0.0"));
        
        invokeFlush();
        
        assertEquals(1, counterMapSize());
    }
    
    private void invokeFlush() throws Exception {
        Method flush = SkillDownloadCountManager.class.getDeclaredMethod("flush");
        flush.setAccessible(true);
        flush.invoke(manager);
    }
    
    private int counterMapSize() {
        return ((java.util.Map<?, ?>) ReflectionTestUtils.getField(manager, "counterMap")).size();
    }
}
