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

package com.alibaba.nacos.api.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTransportModeTest {
    
    @Test
    void parseSupportedValuesIgnoringCase() {
        assertEquals(AgentTransportMode.GRPC, AgentTransportMode.fromValue("GRPC"));
        assertEquals(AgentTransportMode.HTTP, AgentTransportMode.fromValue("http"));
        assertEquals(AgentTransportMode.AUTO, AgentTransportMode.fromValue("Auto"));
        assertEquals("grpc", AgentTransportMode.GRPC.getValue());
        assertEquals("http", AgentTransportMode.HTTP.getValue());
        assertEquals("auto", AgentTransportMode.AUTO.getValue());
    }
    
    @Test
    void rejectNullPaddedAndUnknownValues() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentTransportMode.fromValue(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentTransportMode.fromValue(" auto "));
        assertThrows(IllegalArgumentException.class,
            () -> AgentTransportMode.fromValue("unknown"));
    }
}
