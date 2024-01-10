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

package com.alibaba.nacos.core.model.vo;

import org.junit.Assert;
import org.junit.Test;

public class InstanceIdGeneratorVOTest {
    
    @Test
    public void test() {
        IdGeneratorVO vo = new IdGeneratorVO();
        IdGeneratorVO.IdInfo info = new IdGeneratorVO.IdInfo();
        info.setWorkerId(1L);
        info.setCurrentId(2L);
        vo.setResource("test");
        vo.setInfo(info);
    
        Assert.assertEquals(vo.getInfo(), info);
        Assert.assertEquals(vo.getResource(), "test");
        Assert.assertEquals(vo.getInfo().getWorkerId().longValue(), 1L);
        Assert.assertEquals(vo.getInfo().getCurrentId().longValue(), 2L);
    }
}
