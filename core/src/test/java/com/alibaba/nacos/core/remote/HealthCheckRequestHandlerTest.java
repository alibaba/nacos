/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link HealthCheckRequestHandler} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 19:17
 */
class HealthCheckRequestHandlerTest {
    
    @Test
    void testHandle() {
        HealthCheckRequestHandler handler = new HealthCheckRequestHandler();
        HealthCheckResponse response = handler.handle(null, null);
        assertNotNull(response);
    }
    
    @Test
    void testHandleRequestWhenFilterThrowsStillCallsHandle() throws NacosException {
        HealthCheckRequestHandler handler = new HealthCheckRequestHandler();
        RequestFilters filters = new RequestFilters();
        filters.registerFilter(new AbstractRequestFilter() {
            
            @Override
            protected Response filter(com.alibaba.nacos.api.remote.request.Request request,
                RequestMeta meta,
                Class handlerClazz) throws NacosException {
                throw new RuntimeException("filter throw");
            }
        });
        ReflectionTestUtils.setField(handler, "requestFilters", filters);
        Response result = handler.handleRequest(null, null);
        assertNotNull(result);
    }
    
    @Test
    void testHandleRequestWhenFilterReturnsErrorResponse() throws NacosException {
        HealthCheckRequestHandler handler = new HealthCheckRequestHandler();
        RequestFilters filters = new RequestFilters();
        filters.registerFilter(new AbstractRequestFilter() {
            
            @Override
            protected Response filter(com.alibaba.nacos.api.remote.request.Request request,
                RequestMeta meta,
                Class handlerClazz) throws NacosException {
                HealthCheckResponse err = new HealthCheckResponse();
                err.setErrorInfo(403, "forbidden");
                return err;
            }
        });
        ReflectionTestUtils.setField(handler, "requestFilters", filters);
        Response result = handler.handleRequest(null, null);
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(403, result.getErrorCode());
    }
}
