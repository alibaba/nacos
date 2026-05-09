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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for {@link AgentSpecNoopHandler} — scope update.
 *
 * @author nacos
 */
class AgentSpecNoopHandlerTest {
    
    private AgentSpecNoopHandler agentSpecNoopHandler;
    
    @BeforeEach
    void setUp() {
        agentSpecNoopHandler = new AgentSpecNoopHandler();
    }
    
    @Test
    void testUpdateScopeThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> agentSpecNoopHandler.updateScope(new AgentSpecScopeForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
    
    @Test
    void testForcePublishThrowsNotImplemented() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> agentSpecNoopHandler.forcePublish(new AgentSpecPublishForm()));
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, ex.getErrCode());
    }
}
