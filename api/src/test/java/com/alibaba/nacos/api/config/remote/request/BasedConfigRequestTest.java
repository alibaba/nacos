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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.remote.request.Request;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class BasedConfigRequestTest {
    
    protected static ObjectMapper mapper;
    
    protected static final String DATA_ID = "test_data";
    
    protected static final String GROUP = "group";
    
    protected static final String TENANT = "test_tenant";
    
    protected static final String MD5 = "test_MD5";
    
    protected static final String TAG = "tag";
    
    protected static final String[] KEY = new String[] {DATA_ID, GROUP, TENANT};
    
    protected static final Map<String, String> HEADERS = new HashMap<>();
    
    protected static final String HEADER_KEY = "header1";
    
    protected static final String HEADER_VALUE = "test_header1";
    
    protected static final String CONTENT = "content";
    
    static {
        HEADERS.put(HEADER_KEY, HEADER_VALUE);
    }
    
    @BeforeClass
    public static void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }
    
    public abstract void testSerialize() throws JsonProcessingException;
    
    public abstract void testDeserialize() throws JsonProcessingException;
    
    protected String injectRequestUuId(Request request) {
        String uuid = UUID.randomUUID().toString();
        request.setRequestId(uuid);
        return uuid;
    }
}
