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

package com.alibaba.nacos.core.distributed.distro.task.delay;

import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link DistroDelayTaskExecuteEngine} unit test.
 */
class DistroDelayTaskExecuteEngineTest {
    
    private DistroDelayTaskExecuteEngine engine;
    
    @BeforeEach
    void setUp() {
        engine = new DistroDelayTaskExecuteEngine();
    }
    
    @Test
    void testAddAndGetProcessorWithStringKey() {
        NacosTaskProcessor processor = task -> true;
        engine.addProcessor("type1", processor);
        assertSame(processor, engine.getProcessor("type1"));
    }
    
    @Test
    void testAddAndGetProcessorWithDistroKey() {
        NacosTaskProcessor processor = task -> true;
        DistroKey key = new DistroKey("resourceKey", "resourceType");
        engine.addProcessor(key, processor);
        assertSame(processor, engine.getProcessor(key));
        assertSame(processor, engine.getProcessor("resourceType"));
    }
    
    @Test
    void testGetProcessorWithDistroKeyReturnsByResourceType() {
        NacosTaskProcessor processor = task -> true;
        engine.addProcessor("myType", processor);
        DistroKey key = new DistroKey("any", "myType", "server");
        assertSame(processor, engine.getProcessor(key));
    }
}
