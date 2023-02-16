/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.response;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.Assert;
import org.junit.Test;

public class ErrorResponseTest {
    
    @Test
    public void testBuildWithErrorCode() {
        int errorCode = 500;
        String msg = "err msg";
        
        Response response = ErrorResponse.build(errorCode, msg);
        
        Assert.assertEquals(errorCode, response.getErrorCode());
        Assert.assertEquals(msg, response.getMessage());
    }
    
    @Test
    public void testBuildWithThrowable() {
        String errMsg = "exception msg";
        RuntimeException runtimeException = new RuntimeException(errMsg);
        
        Response response = ErrorResponse.build(runtimeException);
        
        Assert.assertEquals(ResponseCode.FAIL.getCode(), response.getErrorCode());
        Assert.assertEquals(errMsg, response.getMessage());
    }
    
    @Test
    public void testBuildWithNacosException() {
        int errCode = 500;
        String errMsg = "nacos exception msg";
        NacosException nacosException = new NacosException(errCode, errMsg);
        
        Response response = ErrorResponse.build(nacosException);
        
        Assert.assertEquals(errCode, response.getErrorCode());
        Assert.assertEquals(errMsg, response.getMessage());
    }
    
    @Test
    public void testBuildWithNacosRuntimeException() {
        int errCode = 500;
        String errMsg = "nacos runtime exception msg";
        NacosRuntimeException nacosRuntimeException = new NacosRuntimeException(errCode, errMsg);
        
        Response response = ErrorResponse.build(nacosRuntimeException);
        
        Assert.assertEquals(errCode, response.getErrorCode());
        Assert.assertEquals("errCode: " + errCode + ", errMsg: " + errMsg + " ", response.getMessage());
    }
    
}
