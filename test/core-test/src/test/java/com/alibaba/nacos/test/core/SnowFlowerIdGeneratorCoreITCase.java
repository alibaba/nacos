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

package com.alibaba.nacos.test.core;

import com.alibaba.nacos.core.distributed.id.SnowFlowerIdGenerator;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Integration test case for validating unique ID generation using SnowFlowerIdGenerator with distinct initializations
 * and assertions for uniqueness.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class SnowFlowerIdGeneratorCoreITCase {
    
    @Test
    void testIdGenerator() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        SnowFlowerIdGenerator generator1 = new SnowFlowerIdGenerator();
        SnowFlowerIdGenerator generator2 = new SnowFlowerIdGenerator();
        SnowFlowerIdGenerator generator3 = new SnowFlowerIdGenerator();
        
        generator1.initialize(1);
        generator2.initialize(2);
        generator3.initialize(3);
        
        long id1 = generator1.nextId();
        long id2 = generator2.nextId();
        long id3 = generator3.nextId();
        
        assertNotEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id3);
        
    }
    
}
