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

package com.alibaba.nacos.common.model;

import com.alibaba.nacos.common.model.core.IResultCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RestResultUtilsTest {
    
    @Test
    public void testSuccessWithDefault() {
        RestResult<Object> restResult = RestResultUtils.success();
        assertRestResult(restResult, 200, null, null, true);
    }
    
    @Test
    public void testSuccessWithData() {
        RestResult<String> restResult = RestResultUtils.success("content");
        assertRestResult(restResult, 200, null, "content", true);
    }
    
    @Test
    public void testSuccessWithMsg() {
        RestResult<String> restResult = RestResultUtils.success("test", "content");
        assertRestResult(restResult, 200, "test", "content", true);
    }
    
    @Test
    public void testSuccessWithCode() {
        RestResult<String> restResult = RestResultUtils.success(203, "content");
        assertRestResult(restResult, 203, null, "content", false);
    }
    
    @Test
    public void testFailedWithDefault() {
        RestResult<Object> restResult = RestResultUtils.failed();
        assertRestResult(restResult, 500, null, null, false);
    }
    
    @Test
    public void testFailedWithMsg() {
        RestResult<String> restResult = RestResultUtils.failed("test");
        assertRestResult(restResult, 500, "test", null, false);
    }
    
    @Test
    public void testFailedWithCode() {
        RestResult<String> restResult = RestResultUtils.failed(400, "content");
        assertRestResult(restResult, 400, null, "content", false);
    }
    
    @Test
    public void testSuccessWithFull() {
        RestResult<String> restResult = RestResultUtils.failed(400, "content", "test");
        assertRestResult(restResult, 400, "test", "content", false);
    }
    
    @Test
    public void testFailedWithMsgMethod() {
        RestResult<String> restResult = RestResultUtils.failedWithMsg(400, "content");
        assertRestResult(restResult, 400, "content", null, false);
    }
    
    @Test
    public void testBuildResult() {
        IResultCode mockCode = new IResultCode() {
            @Override
            public int getCode() {
                return 503;
            }
            
            @Override
            public String getCodeMsg() {
                return "limited";
            }
        };
        RestResult<String> restResult = RestResultUtils.buildResult(mockCode, "content");
        assertRestResult(restResult, 503, "limited", "content", false);
    }
    
    private void assertRestResult(RestResult restResult, int code, String message, Object data, boolean isOk) {
        assertEquals(code, restResult.getCode());
        assertEquals(message, restResult.getMessage());
        assertEquals(data, restResult.getData());
        assertEquals(isOk, restResult.ok());
    }
}