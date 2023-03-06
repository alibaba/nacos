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

package com.alibaba.nacos.test.config;

import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.config.server.service.sql.ModifyRequest;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class EmbeddedStorageContextUtils_CITCase {
    
    @BeforeClass
    @AfterClass
    public static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
        ConfigCleanUtils.changeToNewTestNacosHome(EmbeddedStorageContextUtils_CITCase.class.getSimpleName());
    }
    
    @Test
    public void test_multi_thread_sql_contexts() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        
        ExecutorService service = Executors.newFixedThreadPool(3);
        for (int i = 1; i < 4; i++) {
            final int j = i;
            service.submit(() -> {
                try {
                    EmbeddedStorageContextUtils.addSqlContext("test_" + j, j);
                    EmbeddedStorageContextUtils.addSqlContext("test_" + j * 10, j);
                    
                    List<ModifyRequest> list = EmbeddedStorageContextUtils.getCurrentSqlContext();
                    System.out.println(list);
                    Assert.assertEquals("test_" + j, list.get(0).getSql());
                    Assert.assertEquals("test_" + j * 10, list.get(0).getSql());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        
    }
    
}
