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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdHttpClientRequestInterceptorTest {
    
    private static final URI TEST_URI = URI.create("http://localhost/nacos/test");
    
    private final RequestIdHttpClientRequestInterceptor interceptor =
        new RequestIdHttpClientRequestInterceptor();
    
    @Test
    void testPreservesOtherHeadersAndCallerHeaders() {
        Header headers = Header.newInstance()
            .addParam("nacos-request-id", "previous-id")
            .addParam(HttpHeaderConsts.REQUEST_ID, "legacy-id")
            .addParam("X-Nacos-Client-Id", "client-id")
            .addParam(HttpHeaderConsts.REQUEST_MODULE, "ai");
        RequestHttpEntity entity = new RequestHttpEntity(headers, null);
        
        assertFalse(interceptor.isIntercept(TEST_URI, "GET", entity));
        
        assertEquals(4, UUID.fromString(entity.getHeaders()
            .getValue(HttpHeaderConsts.NACOS_REQUEST_ID)).version());
        assertEquals("legacy-id", entity.getHeaders().getValue(HttpHeaderConsts.REQUEST_ID));
        assertEquals("client-id", entity.getHeaders().getValue("X-Nacos-Client-Id"));
        assertEquals("ai", entity.getHeaders().getValue(HttpHeaderConsts.REQUEST_MODULE));
        assertEquals(headers.getHeader().size(), entity.getHeaders().getHeader().size());
        assertEquals("previous-id", headers.getValue(HttpHeaderConsts.NACOS_REQUEST_ID));
    }
    
    @Test
    void testGeneratesFreshIdForReusedRequestEntity() {
        RequestHttpEntity entity = new RequestHttpEntity(Header.newInstance(), null);
        assertFalse(interceptor.isIntercept(TEST_URI, "POST", entity));
        String firstId = entity.getHeaders().getValue(HttpHeaderConsts.NACOS_REQUEST_ID);
        
        assertFalse(interceptor.isIntercept(TEST_URI, "POST", entity));
        
        String secondId = entity.getHeaders().getValue(HttpHeaderConsts.NACOS_REQUEST_ID);
        assertEquals(4, UUID.fromString(firstId).version());
        assertEquals(4, UUID.fromString(secondId).version());
        assertNotEquals(firstId, secondId);
    }
    
    @Test
    void testDoesNotMutateSharedEmptyHeaders() {
        RequestHttpEntity first = new RequestHttpEntity(Header.EMPTY, null);
        RequestHttpEntity second = new RequestHttpEntity(null, (Object) null);
        
        assertFalse(interceptor.isIntercept(TEST_URI, "GET", first));
        assertFalse(interceptor.isIntercept(TEST_URI, "GET", second));
        
        String firstId = first.getHeaders().getValue(HttpHeaderConsts.NACOS_REQUEST_ID);
        String secondId = second.getHeaders().getValue(HttpHeaderConsts.NACOS_REQUEST_ID);
        assertEquals(4, UUID.fromString(firstId).version());
        assertEquals(4, UUID.fromString(secondId).version());
        assertNotEquals(firstId, secondId);
        assertNull(Header.EMPTY.getValue(HttpHeaderConsts.NACOS_REQUEST_ID));
    }
}
