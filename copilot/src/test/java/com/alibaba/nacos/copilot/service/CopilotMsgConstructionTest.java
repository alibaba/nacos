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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.service;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Compatibility test for the AgentScope message construction used across Copilot services.
 *
 * <p>AgentScope 2.0 validates {@code Msg} content against its role at construction time
 * (migration guide A.6). Copilot only ever builds text messages, in three shapes:
 * {@code textContent + USER}, {@code textContent + ASSISTANT}, and {@code textContent}
 * with no explicit role. These cases lock in that all three still build successfully after
 * the upgrade, so it cannot silently break prompt/skill message assembly at runtime.
 *
 * @author nacos
 */
class CopilotMsgConstructionTest {
    
    @Test
    void testTextMessageWithUserRole() {
        Msg msg = Msg.builder()
            .textContent("optimize this skill")
            .role(MsgRole.USER)
            .build();
        
        assertNotNull(msg);
        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals("optimize this skill", msg.getTextContent());
    }
    
    @Test
    void testTextMessageWithAssistantRole() {
        Msg msg = Msg.builder()
            .textContent("I received the skill info")
            .role(MsgRole.ASSISTANT)
            .build();
        
        assertNotNull(msg);
        assertEquals(MsgRole.ASSISTANT, msg.getRole());
        assertEquals("I received the skill info", msg.getTextContent());
    }
    
    @Test
    void testTextMessageWithoutExplicitRole() {
        Msg msg = Msg.builder()
            .textContent("user input without explicit role")
            .build();
        
        assertNotNull(msg);
        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals("user input without explicit role", msg.getTextContent());
    }
}
