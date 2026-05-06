/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.trace.event.naming;

import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceTraceEventTest extends NamingTraceEventTest {
    
    @Test
    void testRegisterInstanceTraceEvent() {
        RegisterInstanceTraceEvent registerInstanceTraceEvent = new RegisterInstanceTraceEvent(TIME, CLIENT_IP, true,
                NAMESPACE_ID, GROUP_NAME, SERVICE_NAME, IP, PORT);
        assertBasicInfo(registerInstanceTraceEvent);
        assertEquals("REGISTER_INSTANCE_TRACE_EVENT", registerInstanceTraceEvent.getType());
        assertEquals(CLIENT_IP, registerInstanceTraceEvent.getClientIp());
        assertTrue(registerInstanceTraceEvent.isRpc());
        assertEquals(IP, registerInstanceTraceEvent.getInstanceIp());
        assertEquals(PORT, registerInstanceTraceEvent.getInstancePort());
        assertEquals(IP + ":" + PORT, registerInstanceTraceEvent.toInetAddr());
    }
    
    @Test
    void testDeregisterInstanceTraceEvent() {
        DeregisterInstanceTraceEvent deregisterInstanceTraceEvent = new DeregisterInstanceTraceEvent(TIME, CLIENT_IP, true,
                DeregisterInstanceReason.NATIVE_DISCONNECTED, NAMESPACE_ID, GROUP_NAME, SERVICE_NAME, IP, PORT);
        assertBasicInfo(deregisterInstanceTraceEvent);
        assertEquals("DEREGISTER_INSTANCE_TRACE_EVENT", deregisterInstanceTraceEvent.getType());
        assertEquals(CLIENT_IP, deregisterInstanceTraceEvent.getClientIp());
        assertTrue(deregisterInstanceTraceEvent.isRpc());
        assertEquals(IP, deregisterInstanceTraceEvent.getInstanceIp());
        assertEquals(PORT, deregisterInstanceTraceEvent.getInstancePort());
        assertEquals(IP + ":" + PORT, deregisterInstanceTraceEvent.toInetAddr());
        assertEquals(DeregisterInstanceReason.NATIVE_DISCONNECTED, deregisterInstanceTraceEvent.getReason());
    }
    
    @Test
    void testUpdateInstanceTraceEvent() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("test1", "testValue");
        UpdateInstanceTraceEvent updateInstanceTraceEvent = new UpdateInstanceTraceEvent(TIME, CLIENT_IP, NAMESPACE_ID,
                GROUP_NAME, SERVICE_NAME, IP, PORT, metadata);
        assertBasicInfo(updateInstanceTraceEvent);
        assertEquals("UPDATE_INSTANCE_TRACE_EVENT", updateInstanceTraceEvent.getType());
        assertEquals(CLIENT_IP, updateInstanceTraceEvent.getClientIp());
        assertEquals(IP, updateInstanceTraceEvent.getInstanceIp());
        assertEquals(PORT, updateInstanceTraceEvent.getInstancePort());
        assertEquals(IP + ":" + PORT, updateInstanceTraceEvent.toInetAddr());
        assertEquals(metadata, updateInstanceTraceEvent.getMetadata());
    }
}