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
package com.alibaba.nacos.client.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Json tool
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JSONUtils {

    private static ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static String serializeObject(Object o) throws IOException {
        return mapper.writeValueAsString(o);
    }

    public static Object deserializeObject(String s, Class<?> clazz) throws IOException {
        return mapper.readValue(s, clazz);
    }

    public static <T> T deserializeObject(String s, TypeReference<T> typeReference)
        throws IOException {
        return mapper.readValue(s, typeReference);
    }

}
