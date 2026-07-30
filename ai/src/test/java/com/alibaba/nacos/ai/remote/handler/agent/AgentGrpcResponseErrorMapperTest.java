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

package com.alibaba.nacos.ai.remote.handler.agent;

import com.alibaba.nacos.api.ai.remote.response.AgentEndpointOperationResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentGrpcResponseErrorMapperTest {
    
    @Test
    void testMapsSupportedExceptionCategories() throws Exception {
        assertError(new NacosApiException(404, ErrorCode.RESOURCE_NOT_FOUND, "missing"),
            ErrorCode.RESOURCE_NOT_FOUND.getCode(), "missing");
        assertError(new IllegalArgumentException("invalid"),
            ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), "invalid");
        
        NacosRuntimeException runtimeException = new NacosRuntimeException(12345, "runtime");
        assertError(runtimeException, 12345, runtimeException.getMessage());
        assertError(new NacosException(409, "conflict"), 409, "conflict");
        assertError(new Exception("unexpected"), ErrorCode.SERVER_ERROR.getCode(), "unexpected");
        
        Constructor<AgentGrpcResponseErrorMapper> constructor =
            AgentGrpcResponseErrorMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
    
    private void assertError(Exception exception, int errorCode, String message) {
        Response response = new AgentEndpointOperationResponse();
        AgentGrpcResponseErrorMapper.apply(response, exception);
        assertEquals(errorCode, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
}
