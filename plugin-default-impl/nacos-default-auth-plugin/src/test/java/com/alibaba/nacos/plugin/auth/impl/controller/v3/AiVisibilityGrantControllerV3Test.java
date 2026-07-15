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

package com.alibaba.nacos.plugin.auth.impl.controller.v3;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.plugin.auth.impl.visibility.AiVisibilityGrantInfo;
import com.alibaba.nacos.plugin.auth.impl.visibility.AiVisibilityGrantService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiVisibilityGrantControllerV3Test {
    
    @Test
    void grantShouldDelegateToService() throws Exception {
        AiVisibilityGrantService service = mock(AiVisibilityGrantService.class);
        AiVisibilityGrantControllerV3 controller = new AiVisibilityGrantControllerV3(service);
        
        Result<String> result = controller.grant("public", "skill", "demo-skill", "bob", "r");
        
        verify(service).grant("public", "skill", "demo-skill", "bob", "r");
        assertEquals("grant ai visibility permission ok!", result.getData());
    }
    
    @Test
    void revokeShouldDelegateToService() throws Exception {
        AiVisibilityGrantService service = mock(AiVisibilityGrantService.class);
        AiVisibilityGrantControllerV3 controller = new AiVisibilityGrantControllerV3(service);
        
        Result<String> result = controller.revoke("public", "skill", "demo-skill", "bob", "w");
        
        verify(service).revoke("public", "skill", "demo-skill", "bob", "w");
        assertEquals("revoke ai visibility permission ok!", result.getData());
    }
    
    @Test
    void listShouldReturnServiceResult() throws Exception {
        AiVisibilityGrantService service = mock(AiVisibilityGrantService.class);
        AiVisibilityGrantControllerV3 controller = new AiVisibilityGrantControllerV3(service);
        AiVisibilityGrantInfo info = new AiVisibilityGrantInfo();
        info.setUsername("bob");
        when(service.list("public", "skill", "demo-skill")).thenReturn(List.of(info));
        
        Result<List<AiVisibilityGrantInfo>> result =
            controller.list("public", "skill", "demo-skill");
        
        assertEquals(1, result.getData().size());
        assertEquals("bob", result.getData().get(0).getUsername());
    }
}
