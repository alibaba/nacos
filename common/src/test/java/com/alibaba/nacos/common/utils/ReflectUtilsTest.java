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

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ReflectUtilsTest {

    Subscriber sub;

    @Before
    public void before() {
        sub = new Subscriber<ServerConfigChangeEvent>() {
            @Override
            public void onEvent(ServerConfigChangeEvent event) {

            }
        };
    }

    @Test
    public void findParameterizedTypeReferenceSubclass() {
        Class<?> parameterizedTypeReferenceSubclass = ReflectUtils.findParameterizedTypeReferenceSubclass(
                sub.getClass(), Subscriber.class);
        Assert.assertEquals(parameterizedTypeReferenceSubclass, sub.getClass());

    }

    @Test
    public void findGenericClass() {
        Class<?> genericClass = ReflectUtils.findGenericClass(sub.getClass(), Subscriber.class, 0);
        Assert.assertEquals(genericClass, ServerConfigChangeEvent.class);
    }

}
