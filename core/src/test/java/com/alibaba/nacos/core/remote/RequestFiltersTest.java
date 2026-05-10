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
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RequestFilters} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 19:20
 */
class RequestFiltersTest {
    
    @Test
    void testRegisterFilter() {
        RequestFilters requestFilters = new RequestFilters();
        requestFilters.registerFilter(new AbstractRequestFilter() {
            
            @Override
            protected Response filter(Request request, RequestMeta meta, Class handlerClazz)
                throws NacosException {
                return null;
            }
        });
        
        assertEquals(1, requestFilters.filters.size());
    }
    
    @Test
    void testAbstractRequestFilterInitRegistersToRequestFilters() {
        RequestFilters requestFilters = new RequestFilters();
        AbstractRequestFilter filter = new AbstractRequestFilter() {
            
            @Override
            protected Response filter(Request request, RequestMeta meta, Class handlerClazz)
                throws NacosException {
                return null;
            }
        };
        ReflectionTestUtils.setField(filter, "requestFilters", requestFilters);
        filter.init();
        assertEquals(1, requestFilters.filters.size());
        assertTrue(requestFilters.filters.contains(filter));
    }
    
    @Test
    void testAbstractRequestFilterGetHandleMethodNoSuchMethodThrows() throws Exception {
        AbstractRequestFilter filter = new AbstractRequestFilter() {
            
            @Override
            protected Response filter(Request request, RequestMeta meta, Class handlerClazz)
                throws NacosException {
                return null;
            }
        };
        Method getHandleMethod =
            AbstractRequestFilter.class.getDeclaredMethod("getHandleMethod", Class.class);
        getHandleMethod.setAccessible(true);
        InvocationTargetException wrapped = assertThrows(InvocationTargetException.class,
            () -> getHandleMethod.invoke(filter, String.class));
        NacosException ex = (NacosException) wrapped.getCause();
        assertNotNull(ex);
        assertTrue(ex.getCause() instanceof NoSuchMethodException);
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    void testAbstractRequestFilterGetDefaultResponseInstance() throws Exception {
        AbstractRequestFilter filter = new AbstractRequestFilter() {
            
            @Override
            protected Response filter(Request request, RequestMeta meta, Class handlerClazz)
                throws NacosException {
                return null;
            }
        };
        Method getDefaultResponseInstance = AbstractRequestFilter.class
            .getDeclaredMethod("getDefaultResponseInstance", Class.class);
        getDefaultResponseInstance.setAccessible(true);
        Response resp =
            (Response) getDefaultResponseInstance.invoke(filter, HealthCheckRequestHandler.class);
        assertNotNull(resp);
        assertTrue(resp instanceof com.alibaba.nacos.api.remote.response.HealthCheckResponse);
    }
    
    @Test
    void testAbstractRequestFilterGetDefaultResponseInstanceThrowsWhenInvalidHandler()
        throws Exception {
        AbstractRequestFilter filter = new AbstractRequestFilter() {
            
            @Override
            protected Response filter(Request request, RequestMeta meta, Class handlerClazz)
                throws NacosException {
                return null;
            }
        };
        Method getDefaultResponseInstance = AbstractRequestFilter.class
            .getDeclaredMethod("getDefaultResponseInstance", Class.class);
        getDefaultResponseInstance.setAccessible(true);
        InvocationTargetException wrapped = assertThrows(InvocationTargetException.class,
            () -> getDefaultResponseInstance.invoke(filter,
                HandlerWithNonInstantiableResponse.class));
        assertTrue(wrapped.getCause() instanceof NacosException);
    }
    
    static abstract class AbstractFakeResponse extends Response {
    }
    
    static class HandlerWithNonInstantiableResponse
        extends RequestHandler<Request, AbstractFakeResponse> {
        
        @Override
        public AbstractFakeResponse handle(Request request, RequestMeta meta)
            throws NacosException {
            return null;
        }
    }
}
