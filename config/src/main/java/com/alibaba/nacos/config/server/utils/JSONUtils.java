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
package com.alibaba.nacos.config.server.utils;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import java.io.IOException;


/**
 * json util
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JSONUtils {

    public static String serializeObject(Object o) throws IOException {
        return JSON.toJSONString(o);
    }

    public static Object deserializeObject(String s, Class<?> clazz) throws IOException {
        return JSON.parseObject(s, clazz);
    }

    public static <T> T deserializeObject(String s, TypeReference<T> typeReference)
            throws IOException {
        return JSON.parseObject(s, typeReference);
    }

}
