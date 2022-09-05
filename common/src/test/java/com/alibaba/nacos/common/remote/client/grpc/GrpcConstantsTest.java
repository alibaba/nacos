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

package com.alibaba.nacos.common.remote.client.grpc;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class GrpcConstantsTest {
    
    @Test
    public void testGetRpcParams() {
        Class clazz = GrpcConstants.class;
        Field[] declaredFields = clazz.getDeclaredFields();
        int i = 0;
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            if (declaredField.getType().equals(String.class) && null != declaredField.getAnnotation(
                    GrpcConstants.GRpcConfigLabel.class)) {
                i++;
            }
        }
        assertEquals(i, GrpcConstants.getRpcParams().size());
    }
}
