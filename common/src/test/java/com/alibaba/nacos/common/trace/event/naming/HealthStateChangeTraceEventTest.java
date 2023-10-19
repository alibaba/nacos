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

import com.alibaba.nacos.common.trace.HealthCheckType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HealthStateChangeTraceEventTest extends NamingTraceEventTest {
    
    @Test
    public void testHealthStateChangeTraceEventForClientBeat() {
        HealthStateChangeTraceEvent healthStateChangeTraceEvent = new HealthStateChangeTraceEvent(TIME, NAMESPACE_ID,
                GROUP_NAME, SERVICE_NAME, IP, PORT, false, "client_beat");
        assertBasicInfo(healthStateChangeTraceEvent);
        assertHealthChangeInfo(healthStateChangeTraceEvent);
        assertEquals(HealthCheckType.CLIENT_BEAT, healthStateChangeTraceEvent.getHealthCheckType());
        assertEquals("client_beat", healthStateChangeTraceEvent.getHealthStateChangeReason());
    }
    
    @Test
    public void testHealthStateChangeTraceEventForTcp() {
        HealthStateChangeTraceEvent healthStateChangeTraceEvent = new HealthStateChangeTraceEvent(TIME, NAMESPACE_ID,
                GROUP_NAME, SERVICE_NAME, IP, PORT, false, "tcp:unable2connect:");
        assertBasicInfo(healthStateChangeTraceEvent);
        assertHealthChangeInfo(healthStateChangeTraceEvent);
        assertEquals(HealthCheckType.TCP_SUPER_SENSE, healthStateChangeTraceEvent.getHealthCheckType());
        assertEquals("tcp:unable2connect:", healthStateChangeTraceEvent.getHealthStateChangeReason());
    }
    
    @Test
    public void testHealthStateChangeTraceEventForHttp() {
        HealthStateChangeTraceEvent healthStateChangeTraceEvent = new HealthStateChangeTraceEvent(TIME, NAMESPACE_ID,
                GROUP_NAME, SERVICE_NAME, IP, PORT, false, "http:error:");
        assertBasicInfo(healthStateChangeTraceEvent);
        assertHealthChangeInfo(healthStateChangeTraceEvent);
        assertEquals(HealthCheckType.HTTP_HEALTH_CHECK, healthStateChangeTraceEvent.getHealthCheckType());
        assertEquals("http:error:", healthStateChangeTraceEvent.getHealthStateChangeReason());
    }
    
    @Test
    public void testHealthStateChangeTraceEventForMysql() {
        HealthStateChangeTraceEvent healthStateChangeTraceEvent = new HealthStateChangeTraceEvent(TIME, NAMESPACE_ID,
                GROUP_NAME, SERVICE_NAME, IP, PORT, false, "mysql:timeout:");
        assertBasicInfo(healthStateChangeTraceEvent);
        assertHealthChangeInfo(healthStateChangeTraceEvent);
        assertEquals(HealthCheckType.MYSQL_HEALTH_CHECK, healthStateChangeTraceEvent.getHealthCheckType());
        assertEquals("mysql:timeout:", healthStateChangeTraceEvent.getHealthStateChangeReason());
    }
    
    private void assertHealthChangeInfo(HealthStateChangeTraceEvent event) {
        assertEquals("HEALTH_STATE_CHANGE_TRACE_EVENT", event.getType());
        assertEquals(IP, event.getInstanceIp());
        assertEquals(PORT, event.getInstancePort());
        assertEquals(IP + ":" + PORT, event.toInetAddr());
        assertFalse(event.isHealthy());
    }
    
}