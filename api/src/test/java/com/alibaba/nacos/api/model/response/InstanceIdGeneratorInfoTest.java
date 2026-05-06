/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceIdGeneratorInfoTest {
    
    @Test
    void test() {
        IdGeneratorInfo vo = new IdGeneratorInfo();
        IdGeneratorInfo.IdInfo info = new IdGeneratorInfo.IdInfo();
        info.setWorkerId(1L);
        info.setCurrentId(2L);
        vo.setResource("test");
        vo.setInfo(info);
        
        assertEquals(vo.getInfo(), info);
        assertEquals("test", vo.getResource());
        assertEquals(1L, vo.getInfo().getWorkerId().longValue());
        assertEquals(2L, vo.getInfo().getCurrentId().longValue());
        
        assertEquals("IdGeneratorVO{resource='test', info=IdInfo{currentId=2, workerId=1}}", vo.toString());
    }
}
