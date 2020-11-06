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

package com.alibaba.nacos.common.spi;

import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class NacosServiceLoaderTest {
    
    @Test
    public void testLoad() {
        Collection<SpiTestInterface> actual = NacosServiceLoader.load(SpiTestInterface.class);
        assertEquals(1, actual.size());
        assertEquals(SpiTestImpl.class, actual.iterator().next().getClass());
    }
    
    @Test
    public void newServiceInstances() {
        SpiTestInterface loadInstance = NacosServiceLoader.load(SpiTestInterface.class).iterator().next();
        Collection<SpiTestInterface> actual = NacosServiceLoader.newServiceInstances(SpiTestInterface.class);
        assertEquals(1, actual.size());
        assertEquals(SpiTestImpl.class, actual.iterator().next().getClass());
        assertNotEquals(loadInstance, actual.iterator().next());
    }
}
