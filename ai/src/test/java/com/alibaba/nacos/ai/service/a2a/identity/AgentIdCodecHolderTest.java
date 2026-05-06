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

package com.alibaba.nacos.ai.service.a2a.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentIdCodecHolderTest {
    
    @Mock
    private ObjectProvider<AgentIdCodec> agentIdCodecsProvider;
    
    @Mock
    private AgentIdCodec agentIdCodec;
    
    private AgentIdCodecHolder agentIdCodecHolder;
    
    @BeforeEach
    void setUp() {
        when(agentIdCodecsProvider.getIfAvailable(any())).thenReturn(agentIdCodec);
        agentIdCodecHolder = new AgentIdCodecHolder(agentIdCodecsProvider);
    }
    
    @Test
    void testConstructorWithNoAvailableCodec() {
        when(agentIdCodecsProvider.getIfAvailable(any())).thenCallRealMethod();
        
        assertDoesNotThrow(() -> new AgentIdCodecHolder(agentIdCodecsProvider));
    }
    
    @Test
    void testEncode() {
        when(agentIdCodec.encode("test-agent")).thenReturn("encoded-agent");
        
        String result = agentIdCodecHolder.encode("test-agent");
        
        assertEquals("encoded-agent", result);
    }
    
    @Test
    void testEncodeForSearch() {
        when(agentIdCodec.encodeForSearch("test-agent")).thenReturn("encoded-search-agent");
        
        String result = agentIdCodecHolder.encodeForSearch("test-agent");
        
        assertEquals("encoded-search-agent", result);
    }
    
    @Test
    void testDecode() {
        when(agentIdCodec.decode("encoded-agent")).thenReturn("test-agent");
        
        String result = agentIdCodecHolder.decode("encoded-agent");
        
        assertEquals("test-agent", result);
    }
}