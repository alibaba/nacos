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

package com.alibaba.nacos.api.exception.api;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NacosApiExceptionTest {
    
    @Test
    public void testEmptyConstructor() {
        NacosApiException exception = new NacosApiException();
        assertEquals(0, exception.getErrCode());
        assertEquals(0, exception.getDetailErrCode());
        assertEquals(Constants.NULL, exception.getErrMsg());
        assertEquals(Constants.NULL, exception.getErrAbstract());
    }
    
    @Test
    public void testConstructorWithoutCause() {
        NacosApiException exception = new NacosApiException(500, ErrorCode.SERVER_ERROR, "test");
        assertEquals(500, exception.getErrCode());
        assertEquals(ErrorCode.SERVER_ERROR.getCode().intValue(), exception.getDetailErrCode());
        assertEquals("test", exception.getErrMsg());
        assertEquals(ErrorCode.SERVER_ERROR.getMsg(), exception.getErrAbstract());
    }
    
    @Test
    public void testConstructorWithCause() {
        NacosApiException exception = new NacosApiException(500, ErrorCode.SERVER_ERROR,
                new RuntimeException("cause test"), "test");
        assertEquals(500, exception.getErrCode());
        assertEquals(ErrorCode.SERVER_ERROR.getCode().intValue(), exception.getDetailErrCode());
        assertEquals("test", exception.getErrMsg());
        assertEquals(ErrorCode.SERVER_ERROR.getMsg(), exception.getErrAbstract());
    }
}