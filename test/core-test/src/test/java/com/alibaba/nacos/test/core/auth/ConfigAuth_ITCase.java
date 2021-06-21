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
package com.alibaba.nacos.test.core.auth;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author nkorange
 * @since 1.2.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigAuth_ITCase extends AuthBase {

    @LocalServerPort
    private int port;

    public static final long TIME_OUT = 2000;

    public ConfigService iconfig = null;

    private String dataId = "yanlin";
    private String group = "yanlin";

    @Before
    public void init() throws Exception {
        super.init(port);
    }

    @After
    public void destroy(){
        super.destroy();
        try {
            iconfig.shutDown();
        }catch (NacosException ex) {

        }
    }


    @Test
    public void writeWithReadPermission() throws Exception {

        // Construct configService:
        properties.put(PropertyKeyConst.USERNAME, username1);
        properties.put(PropertyKeyConst.PASSWORD, password1);
        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
        iconfig = NacosFactory.createConfigService(properties);

        final String content = "test";
        assertFalse(iconfig.draftConfig(dataId, group, content));
        assertFalse(iconfig.publishConfigFromDraft(dataId, group));
        assertFalse(iconfig.removeDraftConfig(dataId, group));
        assertFalse(iconfig.publishConfig(dataId, group, content));
        assertFalse(iconfig.removeConfig(dataId, group));
    }

    @Test
    public void readWithReadPermission() throws Exception {

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger ai = new AtomicInteger(0);

        properties.put(PropertyKeyConst.USERNAME, username1);
        properties.put(PropertyKeyConst.PASSWORD, password1);
        iconfig = NacosFactory.createConfigService(properties);

        final String content1 = "test" + System.currentTimeMillis();
        System.out.println("content1:" + content1);
        TimeUnit.SECONDS.sleep(1L);
        final String content2 = "test" + System.currentTimeMillis();
        System.out.println("content2:" + content2);

        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content1.equals(cci.getNewValue()) &&
                        !content2.equals(cci.getNewValue())) {
                    return;
                }
                latch.countDown();
            }
        });

        TimeUnit.SECONDS.sleep(3L);

        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        ConfigService configService = NacosFactory.createConfigService(properties);

        boolean result = configService.publishConfig(dataId, group, content1);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        String res = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content1, res);

        latch.await(5L, TimeUnit.SECONDS);
        Assert.assertEquals(1, latch.getCount());

        result = configService.draftConfig(dataId, group, content2);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        res = iconfig.getDraftConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content2, res);

        result = configService.publishConfigFromDraft(dataId, group);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        res = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content2, res);

        latch.await(5L, TimeUnit.SECONDS);
        Assert.assertEquals(0, latch.getCount());
    }

    @Test
    public void writeWithWritePermission() throws Exception {

        // Construct configService:
        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        iconfig = NacosFactory.createConfigService(properties);

        final String content = "test";

        boolean res = iconfig.draftConfig(dataId, group, content);
        Assert.assertTrue(res);

        res = iconfig.removeDraftConfig(dataId, group);
        Assert.assertTrue(res);

        res = iconfig.draftConfig(dataId, group, content);
        Assert.assertTrue(res);

        res = iconfig.publishConfigFromDraft(dataId, group);
        Assert.assertTrue(res);

        res = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(res);

        res = iconfig.removeConfig(dataId, group);
        Assert.assertTrue(res);
    }

    @Test
    public void readWithWritePermission() throws Exception {

        CountDownLatch latch = new CountDownLatch(1);

        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
        properties.put(PropertyKeyConst.USERNAME, username2);
        properties.put(PropertyKeyConst.PASSWORD, password2);
        iconfig = NacosFactory.createConfigService(properties);

        final String content = "test" + System.currentTimeMillis();

        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content.equals(cci.getNewValue())) {
                    return;
                }
                latch.countDown();
            }
        });

        TimeUnit.SECONDS.sleep(3L);

        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);
        result = iconfig.draftConfig(dataId, group, content);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        try {
            iconfig.getDraftConfig(dataId, group, TIME_OUT);
            fail();
        } catch (NacosException ne) {
            Assert.assertEquals(HttpStatus.SC_FORBIDDEN, ne.getErrCode());
        }

        try {
            iconfig.getConfig(dataId, group, TIME_OUT);
            fail();
        } catch (NacosException ne) {
            Assert.assertEquals(HttpStatus.SC_FORBIDDEN, ne.getErrCode());
        }

        latch.await(5L, TimeUnit.SECONDS);

        Assert.assertTrue(latch.getCount() > 0);
    }


    @Test
    public void readWriteWithFullPermission() throws Exception {

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger ai = new AtomicInteger(0);

        properties.put(PropertyKeyConst.USERNAME, username3);
        properties.put(PropertyKeyConst.PASSWORD, password3);
        iconfig = NacosFactory.createConfigService(properties);

        final String content1 = "test" + System.currentTimeMillis();
        System.out.println("content1:" + content1);
        TimeUnit.SECONDS.sleep(1L);
        final String content2 = "test" + System.currentTimeMillis();
        System.out.println("content2:" + content2);

        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content1.equals(cci.getNewValue()) &&
                        !content2.equals(cci.getNewValue())) {
                    return;
                }
                latch.countDown();
            }
        });

        TimeUnit.SECONDS.sleep(3L);

        boolean result = iconfig.publishConfig(dataId, group, content1);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        String res = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content1, res);

        latch.await(5L, TimeUnit.SECONDS);

        Assert.assertEquals(1, latch.getCount());

        result = iconfig.draftConfig(dataId, group, content2);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        res = iconfig.getDraftConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content2, res);

        result = iconfig.publishConfigFromDraft(dataId, group);
        Assert.assertTrue(result);
        TimeUnit.SECONDS.sleep(5L);

        res = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content2, res);

        latch.await(5L, TimeUnit.SECONDS);

        Assert.assertEquals(0, latch.getCount());

        result = iconfig.removeConfig(dataId, group);
        Assert.assertTrue(result);
    }

}
