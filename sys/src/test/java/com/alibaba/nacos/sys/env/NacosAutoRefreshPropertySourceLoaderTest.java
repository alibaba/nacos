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

package com.alibaba.nacos.sys.env;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = NacosAutoRefreshPropertySourceLoaderTest.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class NacosAutoRefreshPropertySourceLoaderTest {
    
    @Autowired
    private ConfigurableEnvironment environment;
    
    private static String oldConfPath = "";
    
    @BeforeClass
    public static void before() throws URISyntaxException {
        oldConfPath = ApplicationUtils.getConfFilePath();
        ApplicationUtils.setConfFilePath(new File(ClassLoader.getSystemResource("application.properties").toURI()).getParent());
    }
    
    @AfterClass
    public static void after() {
        ApplicationUtils.setConfFilePath(oldConfPath);
    }

    @Test
    public void testConfigFileAutoRefresh() throws URISyntaxException, InterruptedException, NacosException, IOException {
        final URL url = ClassLoader.getSystemResource("application.properties");
        ApplicationUtils.setContextPath(url.getPath());
        final String val1 = environment.getProperty("name");
        Assert.assertEquals("test-1", val1);
        final File file = new File(url.toURI());
        final String newKey = "nacos.config.refresh-" + System.currentTimeMillis();
        final String newVal = System.currentTimeMillis() + "-lessspring";
        DiskUtils.writeFile(file, ByteUtils.toBytes("\n" + newKey + "=" + newVal), true);
        CountDownLatch latch = new CountDownLatch(1);
        WatchFileCenter.registerWatcher(ApplicationUtils.getConfFilePath(), new FileWatcher() {
            @Override
            public void onChange(FileChangeEvent event) {
                latch.countDown();
            }
        
            @Override
            public boolean interest(String context) {
                return StringUtils.contains(context, "application.properties");
            }
        });
        latch.await();
        ThreadUtils.sleep(10_000);
        final String val2 = environment.getProperty(newKey);
        Assert.assertEquals(newVal, val2);
    }
    
}