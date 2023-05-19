/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.http.handler;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.TypeUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ResponseHandlerTest {
    
    private final ArrayList<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
    
    @Test
    public void testDeserializationType() throws Exception {
        String json = JacksonUtils.toJson(list);
        ArrayList<Integer> tmp = ResponseHandler.convert(json, TypeUtils.parameterize(List.class, Integer.class));
        Assert.assertEquals(list, tmp);
    }
    
    @Test
    public void testRestResult() throws Exception {
        String json = "{\"code\":200,\"message\":null,\"data\":[{\"USERNAME\":\"nacos\",\"PASSWORD\":" 
                + "\"$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu\",\"ENABLED\":true}]}";
        RestResult<Object> result = ResponseHandler.convert(json, TypeUtils.parameterize(RestResult.class, Object.class));
        Assert.assertEquals(200, result.getCode());
        Assert.assertNull(result.getMessage());
        Assert.assertNotNull(result.getData());
    }
    
    @Test
    public void testDeserializationClass() throws Exception {
        String json = JacksonUtils.toJson(list);
        ArrayList<Integer> tmp = ResponseHandler.convert(json, TypeUtils.parameterize(List.class, Integer.class));
        Assert.assertEquals(list, tmp);
    }
}