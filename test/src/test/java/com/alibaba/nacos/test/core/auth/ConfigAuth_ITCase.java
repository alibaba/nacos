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
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import com.alibaba.nacos.test.base.Params;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

/**
 * @author nkorange
 * @since 1.2.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "server.port=7001"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigAuth_ITCase extends AuthBase {

    @LocalServerPort
    private int port;

    private String accessToken;

    public static final long TIME_OUT = 2000;

    public ConfigService iconfig = null;

    private String dataId = "yanlin";
    private String group = "yanlin";

    private String username = "username1";
    private String password = "password1";
    private String role = "role1";

    private Properties properties;

    private String namespace1 = "namespace1";
    private String namespace2 = "namespace2";

    @Before
    public void init() throws Exception {
        TimeUnit.SECONDS.sleep(5L);
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);

        accessToken = login();

        // Create a user:
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username)
                .appendParam("password", password)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("username", username)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add read permission for namespace1:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "r")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add read/write permission for namespace2:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace2 + ":*:*")
                .appendParam("action", "rw")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Init properties:
        properties = new Properties();
        properties.put(PropertyKeyConst.USERNAME, username);
        properties.put(PropertyKeyConst.PASSWORD, password);
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
    }

    @After
    public void destroy() {

        // Delete permission:
        ResponseEntity<String> response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "r")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete permission:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace2 + ":*:*")
                .appendParam("action", "rw")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("username", username)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username)
                .appendParam("password", password)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }


    @Test
    public void publishConfigWithReadPermission() throws Exception {

        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
        // Construct configService:
        iconfig = NacosFactory.createConfigService(properties);

        final String content = "test";
        try {
            iconfig.publishConfig(dataId, group, content);
            fail();
        } catch (NacosException ne) {
            Assert.assertEquals(HttpStatus.FORBIDDEN.value(), ne.getErrCode());
        }
    }

    @Test
    public void publishConfigWithReadWritePermission() throws Exception {

        properties.put(PropertyKeyConst.NAMESPACE, namespace2);
        // Construct configService:
        iconfig = NacosFactory.createConfigService(properties);

        final String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);

        TimeUnit.SECONDS.sleep(2L);

        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content, value);

        result = iconfig.removeConfig(dataId, group);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);

        TimeUnit.SECONDS.sleep(2L);

        value = iconfig.getConfig(dataId, group, TIME_OUT);
        System.out.println(value);
        Assert.assertNull(value);
    }

    @Test
    public void listenConfigWithReadWritePermission() throws Exception {

        CountDownLatch latch = new CountDownLatch(1);

        final String dataId = "test" + System.currentTimeMillis();
        final String group = "DEFAULT_GROUP";
        final String content = "config data";

        properties.put(PropertyKeyConst.NAMESPACE, namespace2);
        // Construct configService:
        iconfig = NacosFactory.createConfigService(properties);
        iconfig.addListener(dataId, group, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                Assert.assertEquals(null, cci.getOldValue());
                Assert.assertEquals(content, cci.getNewValue());
                Assert.assertEquals(PropertyChangeType.ADDED, cci.getType());
                System.out.println(cci);
                latch.countDown();
            }

        });

        iconfig.publishConfig(dataId, group, content);

        latch.await();
    }

}
