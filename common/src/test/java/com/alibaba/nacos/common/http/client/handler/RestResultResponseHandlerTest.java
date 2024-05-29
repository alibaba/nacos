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

package com.alibaba.nacos.common.http.client.handler;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestResultResponseHandlerTest {
    
    @Test
    void testConvertResult() throws Exception {
        RestResult<String> testCase = RestResult.<String>builder().withCode(200).withData("ok").withMsg("msg").build();
        InputStream inputStream = new ByteArrayInputStream(JacksonUtils.toJsonBytes(testCase));
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(response.getBody()).thenReturn(inputStream);
        when(response.getHeaders()).thenReturn(Header.EMPTY);
        when(response.getStatusCode()).thenReturn(200);
        RestResultResponseHandler<String> handler = new RestResultResponseHandler<>();
        handler.setResponseType(RestResult.class);
        HttpRestResult<String> actual = handler.handle(response);
        assertEquals(testCase.getCode(), actual.getCode());
        assertEquals(testCase.getData(), actual.getData());
        assertEquals(testCase.getMessage(), actual.getMessage());
        assertEquals(Header.EMPTY, actual.getHeader());
    }
}