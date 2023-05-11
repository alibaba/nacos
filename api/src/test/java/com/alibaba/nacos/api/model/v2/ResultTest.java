/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.v2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ResultTest {
    
    @Test
    public void testSuccessEmptyResult() {
        Result<String> result = Result.success();
        assertNull(result.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
    }
    
    @Test
    public void testSuccessWithData() {
        Result<String> result = Result.success("test");
        assertEquals("test", result.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), result.getMessage());
    }
    
    @Test
    public void testFailureMessageResult() {
        Result<String> result = Result.failure("test");
        assertNull(result.getData());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), result.getCode());
        assertEquals("test", result.getMessage());
    }
    
    @Test
    public void testFailureWithoutData() {
        Result<String> result = Result.failure(ErrorCode.DATA_ACCESS_ERROR);
        assertNull(result.getData());
        assertEquals(ErrorCode.DATA_ACCESS_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.DATA_ACCESS_ERROR.getMsg(), result.getMessage());
    }
    
    @Test
    public void testFailureWithData() {
        Result<String> result = Result.failure(ErrorCode.DATA_ACCESS_ERROR, "error");
        assertEquals("error", result.getData());
        assertEquals(ErrorCode.DATA_ACCESS_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.DATA_ACCESS_ERROR.getMsg(), result.getMessage());
    }
    
    @Test
    public void testToString() {
        Result<String> result = Result.success("test");
        assertEquals("Result{errorCode=0, message='success', data=test}", result.toString());
    }
}