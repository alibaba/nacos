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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;
import com.alibaba.nacos.core.remote.HealthCheckRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;

class RemoteParamCheckFilterTest {

    @BeforeEach
    void setUp() {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
    }

    @AfterEach
    void tearDown() {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
    }

    @Test
    void testFilterReturnsNullWhenParamCheckDisabled() throws Exception {
        RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
        Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
                Request.class, RequestMeta.class, Class.class);
        filterMethod.setAccessible(true);
        Object result = filterMethod.invoke(filter, null, null, HealthCheckRequestHandler.class);
        assertNull(result);
    }

    @Test
    void testFilterReturnsNullWhenParamCheckEnabledButNoExtractor() throws Exception {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        try {
            RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
            Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
                    Request.class, RequestMeta.class, Class.class);
            filterMethod.setAccessible(true);
            Object result = filterMethod.invoke(filter, null, null, HealthCheckRequestHandler.class);
            assertNull(result);
        } finally {
            ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
        }
    }
}