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

package com.alibaba.nacos.naming.push.v2.hook;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushResultTest {
    
    private final Service service = Service.newService("namespace", "group", "service");
    
    private final ServiceInfo serviceInfo = new ServiceInfo("group@@service");
    
    private final Subscriber subscriber =
        new Subscriber("1.1.1.1:8848", "agent", "app", "1.1.1.1", "namespace",
            "group@@service", 8848);
    
    @Test
    void testPushSuccessResult() {
        PushResult result = PushResult.pushSuccess(service, "client", serviceInfo, subscriber, 1L,
            2L, 3L, true);
        
        assertTrue(result.isPushSuccess());
        assertSame(service, result.getService());
        assertSame(serviceInfo, result.getData());
        assertSame(subscriber, result.getSubscriber());
        assertNull(result.getException());
    }
    
    @Test
    void testPushFailedResult() {
        RuntimeException exception = new RuntimeException("failed");
        PushResult result = PushResult.pushFailed(service, "client", serviceInfo, subscriber, 1L,
            exception, false);
        
        assertFalse(result.isPushSuccess());
        assertSame(exception, result.getException());
    }
}
