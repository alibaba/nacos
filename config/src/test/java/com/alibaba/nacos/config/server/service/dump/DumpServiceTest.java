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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.service.repository.PersistService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.lang.reflect.Method;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@SpringBootApplication(scanBasePackages = "com.alibaba.nacos")
@WebAppConfiguration
public class DumpServiceTest {
    
    @Autowired
    DumpService service;
    
    @Autowired
    PersistService persistService;
    
    @Test
    public void init() throws Throwable {
        service.init();
    }
    
    @Test
    public void testClearConfigHistoryThresholdRead() throws Exception {
        Class<DumpService> dumpServiceClazz = DumpService.class;
        Method clearConfigHistoryThresholdMethod = dumpServiceClazz.getDeclaredMethod("getRetentionByConfigThreshold");
        clearConfigHistoryThresholdMethod.setAccessible(true);
        int threshold = (int) clearConfigHistoryThresholdMethod.invoke(service);
        Assert.assertEquals(10, threshold);
    }
    
    @Test
    public void testClearConfigHistoryByThreshold() throws Exception {
        persistService.removeConfigHistoryWithRetentionLatest(10);
    }
}
