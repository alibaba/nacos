/*
 *
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
 *
 */

package com.alibaba.nacos.client.monitor.delegate;

import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.client.monitor.TraceMonitor;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import org.junit.Assert;
import org.junit.Test;

public class ServerRequestHandlerTraceDelegateTest extends OpenTelemetryBaseTest {
    
    @Test
    public void testWarp() {
        Request testRequest = new ClientConfigMetricRequest();
        ServerRequestHandler handler = ServerRequestHandlerTraceDelegate.warp((request) -> null);
        Assert.assertNotNull(handler);
        Assert.assertNull(handler.requestReply(testRequest));
        Assert.assertEquals(TraceMonitor.getNacosClientRequestFromServerSpanName() + " / " + testRequest.getModule(),
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
    }
}
