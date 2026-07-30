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

package com.alibaba.nacos.ai.service.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiResourceIndexMaintenanceServiceImpl}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class AiResourceIndexMaintenanceServiceImplTest {
    
    @Mock
    private AiResourceIndexTaskRepository taskRepository;
    
    @Mock
    private AiResourceIndexEnhancementService enhancementService;
    
    private AiResourceIndexMaintenanceService service;
    
    @BeforeEach
    void setUp() {
        service = new AiResourceIndexMaintenanceServiceImpl(taskRepository, enhancementService);
    }
    
    @Test
    void resourceChangeShouldRequestEnabledEnhancement() {
        when(enhancementService.requested()).thenReturn(true);
        
        assertTrue(service.schedule("public", "skill", "avatar"));
        
        verify(taskRepository).schedule("public", "skill", "avatar", true);
    }
    
    @Test
    void resourceChangeShouldNotRequestDisabledEnhancement() {
        assertTrue(service.schedule("public", "skill", "avatar"));
        
        verify(taskRepository).schedule("public", "skill", "avatar", false);
    }
    
    @Test
    void reconciliationShouldRequestEnabledEnhancement() {
        when(enhancementService.requested()).thenReturn(true);
        
        assertTrue(service.scheduleReconciliation("public", "skill", "avatar"));
        
        verify(taskRepository).scheduleReconciliation("public", "skill", "avatar", true);
    }
    
    @Test
    void reconciliationShouldNotRequestDisabledEnhancement() {
        assertTrue(service.scheduleReconciliation("public", "skill", "avatar"));
        
        verify(taskRepository).scheduleReconciliation("public", "skill", "avatar", false);
    }
}
