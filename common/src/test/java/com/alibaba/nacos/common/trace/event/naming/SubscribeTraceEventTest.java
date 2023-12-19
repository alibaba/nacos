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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubscribeTraceEventTest extends NamingTraceEventTest {
    
    @Test
    public void testRegisterInstanceTraceEvent() {
        SubscribeServiceTraceEvent subscribeServiceTraceEvent = new SubscribeServiceTraceEvent(TIME, CLIENT_IP,
                NAMESPACE_ID, GROUP_NAME, SERVICE_NAME);
        assertBasicInfo(subscribeServiceTraceEvent);
        assertEquals("SUBSCRIBE_SERVICE_TRACE_EVENT", subscribeServiceTraceEvent.getType());
        assertEquals(CLIENT_IP, subscribeServiceTraceEvent.getClientIp());
    }
    
    @Test
    public void testDeregisterInstanceTraceEvent() {
        UnsubscribeServiceTraceEvent unsubscribeServiceTraceEvent = new UnsubscribeServiceTraceEvent(TIME, CLIENT_IP,
                NAMESPACE_ID, GROUP_NAME, SERVICE_NAME);
        assertBasicInfo(unsubscribeServiceTraceEvent);
        assertEquals("UNSUBSCRIBE_SERVICE_TRACE_EVENT", unsubscribeServiceTraceEvent.getType());
        assertEquals(CLIENT_IP, unsubscribeServiceTraceEvent.getClientIp());
    }
    
    @Test
    public void testPushServiceTraceEvent() {
        PushServiceTraceEvent pushServiceTraceEvent = new PushServiceTraceEvent(TIME, 10, 510, 510, CLIENT_IP,
                NAMESPACE_ID, GROUP_NAME, SERVICE_NAME, 100);
        assertBasicInfo(pushServiceTraceEvent);
        assertEquals("PUSH_SERVICE_TRACE_EVENT", pushServiceTraceEvent.getType());
        assertEquals(CLIENT_IP, pushServiceTraceEvent.getClientIp());
        assertEquals(10L, pushServiceTraceEvent.getPushCostTimeForNetWork());
        assertEquals(510L, pushServiceTraceEvent.getPushCostTimeForAll());
        assertEquals(510L, pushServiceTraceEvent.getServiceLevelAgreementTime());
        assertEquals(100, pushServiceTraceEvent.getInstanceSize());
        
    }
}