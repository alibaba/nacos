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

package com.alibaba.nacos.core.distributed.id;

import com.alibaba.nacos.consistency.IdGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Test IdGenerator for SPI branch coverage in IdGeneratorManager.
 */
public class TestIdGenerator implements IdGenerator {
    
    private long current = 0;
    
    @Override
    public void init() {
    }
    
    @Override
    public long currentId() {
        return current;
    }
    
    @Override
    public long workerId() {
        return 0;
    }
    
    @Override
    public long nextId() {
        return ++current;
    }
    
    @Override
    public Map<Object, Object> info() {
        Map<Object, Object> map = new HashMap<>(2);
        map.put("currentId", current);
        map.put("workerId", 0L);
        return map;
    }
}
