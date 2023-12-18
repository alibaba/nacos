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
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BeanResponseHandlerTest {
    
    @Test
    public void testConvertResult() throws Exception {
        List<Integer> testCase = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            testCase.add(i);
        }
        byte[] bytes = JacksonUtils.toJsonBytes(testCase);
        InputStream inputStream = new ByteArrayInputStream(bytes);
        HttpClientResponse response = mock(HttpClientResponse.class);
        when(response.getBody()).thenReturn(inputStream);
        when(response.getHeaders()).thenReturn(Header.EMPTY);
        when(response.getStatusCode()).thenReturn(200);
        BeanResponseHandler<List<Integer>> beanResponseHandler = new BeanResponseHandler<>();
        beanResponseHandler.setResponseType(List.class);
        HttpRestResult<List<Integer>> actual = beanResponseHandler.handle(response);
        assertEquals(200, actual.getCode());
        assertEquals(testCase, actual.getData());
        assertNull(actual.getMessage());
        assertEquals(Header.EMPTY, actual.getHeader());
    }
}