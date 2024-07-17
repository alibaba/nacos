/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.spi;

import com.alibaba.nacos.client.address.base.AbstractServerListManager;
import com.alibaba.nacos.client.address.impl.AddressServerListManager;
import com.alibaba.nacos.client.address.impl.FileServerListManager;
import com.alibaba.nacos.client.address.impl.PropertiesServerListManager;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * ServiceLoaderTest.
 *
 * @author misakacoder
 */
public class ServiceLoaderTest {

    @Test
    public void testLoad() throws Exception {
        Map<Class<? extends AbstractServerListManager>, Integer> orderMap = new HashMap<>();
        orderMap.put(PropertiesServerListManager.class, 1);
        orderMap.put(AddressServerListManager.class, 2);
        orderMap.put(FileServerListManager.class, 3);
        List<Class<AbstractServerListManager>> classes = ServiceLoader.load(AbstractServerListManager.class);
        classes.sort(Comparator.comparingInt(orderMap::get));
        List<Class<? extends AbstractServerListManager>> actual = Lists.newArrayList(
                PropertiesServerListManager.class,
                AddressServerListManager.class,
                FileServerListManager.class
        );
        assertEquals(classes, actual);
    }

    @Test
    public void testLoadAndInit() throws Exception {
        Function<Class<AbstractServerListManager>, AbstractServerListManager> init = p -> {
            AbstractServerListManager abstractServerListManager = Mockito.mock(p);
            when(abstractServerListManager.getName()).thenReturn(p.getSimpleName());
            return abstractServerListManager;
        };
        List<AbstractServerListManager> serviceList = ServiceLoader.load(AbstractServerListManager.class, init);
        for (AbstractServerListManager serverListManager : serviceList) {
            assertEquals(serverListManager.getName(), serverListManager.getClass().getSimpleName());
        }
    }
}
