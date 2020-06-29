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

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author xiaochun.xxc
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "server.port=7001"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigAPI_CITCase {

    public static final long TIME_OUT = 5000;
    static ConfigService iconfig = null;
    static HttpAgent agent = null;

    static final String CONFIG_CONTROLLER_PATH = "/v1/cs/configs";
    String SPECIAL_CHARACTERS = "!@#$%^&*()_+-=_|/'?.";
    String dataId = "yanlin";
    String group = "yanlin";

    @LocalServerPort
    private int port;

    @Before
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
        properties.put(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        iconfig = NacosFactory.createConfigService(properties);
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();
    }

    @After
    public void cleanup() throws Exception {
        HttpResult result = null;
        try {
            List<String> params = Arrays.asList("dataId", dataId, "group", group, "beta", "true");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertTrue(JacksonUtils.toObj(result.content).get("data").booleanValue());
            Assert.assertTrue(JacksonUtils.toObj(result.content).get("data").booleanValue());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
    
    @BeforeClass
    @AfterClass
    public static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }

    /**
     * @TCDescription : nacos_正常获取数据
     * @TestStep :
     * @ExpectResult :
     */
    @Test(timeout = 3*TIME_OUT)
    public void nacos_getconfig_1() throws Exception {
        final String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content, value);
        result = iconfig.removeConfig(dataId, group);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);
        value = iconfig.getConfig(dataId, group, TIME_OUT);
        System.out.println(value);
        Assert.assertNull(value);
    }

    /**
     * @TCDescription : nacos_服务端无配置时，获取配置
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_getconfig_2() throws Exception {
        String content = iconfig.getConfig(dataId, "nacos", TIME_OUT);
        Assert.assertNull(content);
    }

    /**
     * @TCDescription : nacos_获取配置时dataId为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_getconfig_3() throws Exception {
        try {
            String content = iconfig.getConfig(null, group, TIME_OUT);
        } catch (Exception e) {
            Assert.assertTrue(true);
            return;
        }
        Assert.fail();
    }

    /**
     * @TCDescription : nacos_获取配置时group为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_getconfig_4() throws Exception {
        final String dataId = "nacos_getconfig_4";
        final String content = "test";
        boolean result = iconfig.publishConfig(dataId, null, content);
        Assert.assertTrue(result);
        Thread.sleep(TIME_OUT);

        String value = iconfig.getConfig(dataId, null, TIME_OUT);
        Assert.assertEquals(content, value);

        result = iconfig.removeConfig(dataId, null);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);
    }

    /**
     * @TCDescription : nacos_服务端无该配置项时，正常创建配置
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_1() throws Exception {
        final String content = "publishConfigTest";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);
        result = iconfig.removeConfig(dataId, group);
        Assert.assertTrue(result);
    }

    /**
     * @TCDescription : nacos_服务端有该配置项时，正常修改配置
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_2() throws Exception {
        final String content = "publishConfigTest";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);

        final String content1 = "test.abc";
        result = iconfig.publishConfig(dataId, group, content1);
        Thread.sleep(TIME_OUT);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content1, value);
    }

    /**
     * @TCDescription : nacos_发布配置时包含特殊字符
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_3() throws Exception {
        String content = "test" + SPECIAL_CHARACTERS;
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);

        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content, value);
    }

    /**
     * @TCDescription : nacos_发布配置时dataId为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_4() throws Exception {
        try {
            String content = "test";
            boolean result = iconfig.publishConfig(null, group, content);
            Thread.sleep(TIME_OUT);
            Assert.assertTrue(result);
        } catch (Exception e) {
            Assert.assertTrue(true);
            return;
        }
        Assert.fail();
    }

    /**
     * @TCDescription : nacos_发布配置时group为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_5() throws Exception {
        final String dataId = "nacos_publishConfig_5";
        String content = "test";
        boolean result = iconfig.publishConfig(dataId, null, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);

        String value = iconfig.getConfig(dataId, null, TIME_OUT);
        Assert.assertEquals(content, value);
    }


    /**
     * @TCDescription : nacos_发布配置时配置内容为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_6() throws Exception {
        String content = null;
        try {
            boolean result = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
        } catch (Exception e) {
            Assert.assertTrue(true);
            return;
        }
        Assert.fail();
    }

    /**
     * @TCDescription : nacos_发布配置时配置内容包含中文字符
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_7() throws Exception {
        String content = "阿里abc";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);
        String value  = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(content, value);
    }

    /**
     * @TCDescription : nacos_服务端有该配置项时，正常删除配置
     * @throws Exception
     */
    @Test
    public void nacos_removeConfig_1() throws Exception {
        String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);

        Assert.assertTrue(result);
        Thread.sleep(TIME_OUT);

        result = iconfig.removeConfig(dataId, group);
        Assert.assertTrue(result);
        Thread.sleep(TIME_OUT);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertNull(value);
    }

    /**
     * @TCDescription : nacos_服务端无该配置项时，配置删除失败
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeConfig_2() throws Exception {
        group += "removeConfig2";
        boolean result = iconfig.removeConfig(dataId, group);
        Assert.assertTrue(result);
    }

    /**
     * @TCDescription : nacos_删除配置时dataId为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeConfig_3() throws Exception {
        try {
            boolean result = iconfig.removeConfig(null, group);
            Assert.assertTrue(result);
        } catch (Exception e) {
            Assert.assertTrue(true);
            return;
        }
        Assert.fail();
    }

    /**
     * @TCDescription : nacos_删除配置时group为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeConfig_4() throws Exception {
        boolean result = iconfig.removeConfig(dataId, null);
        Assert.assertTrue(result);
    }

    /**
     * @TCDescription : nacos_添加对dataId的监听，在服务端修改配置后，获取监听后的修改的配置
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_addListener_1() throws Exception {
        final AtomicInteger count = new AtomicInteger(0);
        final String content = "test-abc";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);

        Listener ml = new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("recieve23:" + configInfo);
                count.incrementAndGet();
                Assert.assertEquals(content, configInfo);
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        };
        iconfig.addListener(dataId, group, ml);
        while (count.get() == 0) {
            Thread.sleep(2000);
        }
        Assert.assertTrue(count.get() >= 1);
        iconfig.removeListener(dataId, group, ml);
    }

    /**
     * @TCDescription : nacos_设置监听器为null，抛出异常信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = TIME_OUT, expected = IllegalArgumentException.class)
    public void nacos_addListener_2() throws Exception {
        iconfig.addListener(dataId, group, null);
    }


    /**
     * @TCDescription : nacos_添加对dataId的监听，修改服务端配置，正常推送并只推送一次
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = Constants.CONFIG_LONG_POLL_TIMEOUT << 2)
    public void nacos_addListener_3() throws InterruptedException, NacosException {
        final AtomicInteger count = new AtomicInteger(0);
        final String dataId = "nacos_addListener_3";
        final String group = "nacos_addListener_3";
        final String content = "test-abc-" + System.currentTimeMillis();
        final String newContent = "nacos_addListener_3-" + System.currentTimeMillis();
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);
        
        // Maximum assurance level notification has been performed
        ThreadUtils.sleep(5000);

        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
                Assert.assertEquals(newContent, configInfo);
            }
        };
        String receive = iconfig.getConfigAndSignListener(dataId, group, 5000L, ml);
        Assert.assertEquals(content, receive);
        result = iconfig.publishConfig(dataId, group, newContent);
        Assert.assertTrue(result);
        // Get enough sleep to ensure that the monitor is triggered only once
        // during the two long training sessions
        ThreadUtils.sleep(Constants.CONFIG_LONG_POLL_TIMEOUT << 1);
        Assert.assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }

    /**
     * @TCDescription : nacos_服务端无配置时，添加对dataId的监听
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_addListener_4() throws Exception {
        final AtomicInteger count = new AtomicInteger(0);

        iconfig.removeConfig(dataId, group);
        Thread.sleep(TIME_OUT);

        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
            }
        };
        iconfig.addListener(dataId, group, ml);
        Thread.sleep(TIME_OUT);
        String content = "test-abc";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);

        while (count.get() == 0) {
            Thread.sleep(3000);
        }
        Assert.assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }

    /**
     * @TCDescription : nacos_在主动拉取配置后并注册Listener，在更新配置后才触发Listener监听事件(使用特定接口)
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author chuntaojun
     * @since 3.6.8
     */
    @Test
    public void nacos_addListener_5() throws InterruptedException, NacosException {
        final AtomicInteger count = new AtomicInteger(0);
        final String dataId = "nacos_addListener_5";
        final String group = "nacos_addListener_5";
        final String content = "test-abc";
        final String newContent = "new-test-def";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);

        Thread.sleep(2000);

        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
                Assert.assertEquals(newContent, configInfo);
            }
        };

        String receiveContent = iconfig.getConfigAndSignListener(dataId, group, 1000, ml);
        System.out.println(receiveContent);

        result = iconfig.publishConfig(dataId, group, newContent);
        Assert.assertTrue(result);

        Assert.assertEquals(content, receiveContent);
        Thread.sleep(2000);

        Assert.assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }

    /**
     * @TCDescription : nacos_在主动拉取配置后并注册Listener，在更新配置后才触发Listener监听事件(进行配置参数设置)
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author chuntaojun
     * @since 3.6.8
     */
    @Test
    public void nacos_addListener_6() throws InterruptedException, NacosException {

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
        properties.put(PropertyKeyConst.ENABLE_REMOTE_SYNC_CONFIG, "true");
        ConfigService iconfig = NacosFactory.createConfigService(properties);

        final AtomicInteger count = new AtomicInteger(0);
        final String dataId = "nacos_addListener_6";
        final String group ="nacos_addListener_6";
        final String content = "test-abc";
        final String newContent = "new-test-def";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);

        Thread.sleep(2000);

        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
                System.out.println("Listener receive : [" + configInfo + "]");
                Assert.assertEquals(newContent, configInfo);
            }
        };

        iconfig.addListener(dataId, group, ml);

        String receiveContent = iconfig.getConfig(dataId, group, 1000);

        System.out.println(receiveContent);

        result = iconfig.publishConfig(dataId, group, newContent);
        Assert.assertTrue(result);

        Thread.sleep(2000);

        receiveContent = iconfig.getConfig(dataId, group, 1000);

        Assert.assertEquals(newContent, receiveContent);

        Assert.assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }

    /**
     * @TCDescription : nacos_正常移除监听器
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeListener_1() throws Exception {
        iconfig.addListener(dataId, group, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                Assert.fail();
            }
        });
        Thread.sleep(TIME_OUT);
        try {
            iconfig.removeListener(dataId, group, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println("remove recieve:" + configInfo);
                }
            });
        } catch (Exception e) {

        }
    }

    /**
     * @TCDescription : nacos_移除无该项dataId的监听器
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = TIME_OUT)
    public void nacos_removeListener_2() {
        group += "test.nacos";
        try {
            iconfig.removeListener(dataId, group, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {

                }
            });
        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_存在多个监听器时，删除最后一个监听器
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeListener_3() throws Exception {
        final String contentRemove = "test-abc-two";
        final AtomicInteger count = new AtomicInteger(0);

        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
            }
        };
        Listener ml1 = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                //System.out.println("ml1 remove listener recieve:" + configInfo);
                count.incrementAndGet();
                Assert.assertEquals(contentRemove, configInfo);
            }
        };
        iconfig.addListener(dataId, group, ml);
        iconfig.addListener(dataId, group, ml1);

        iconfig.removeListener(dataId, group, ml);
        Thread.sleep(TIME_OUT);

        boolean result = iconfig.publishConfig(dataId, group, contentRemove);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);

        while (count.get() == 0) {
            Thread.sleep(3000);
        }
        Assert.assertNotEquals(0, count.get());
    }

    /**
     * @TCDescription : nacos_监听器为null时
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = TIME_OUT)
    public void nacos_removeListener_4() {
        iconfig.removeListener(dataId, group, null);
    }

    /**
     * @TCDescription : nacos_openAPI_配置具体信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 3*TIME_OUT)
    public void nacos_openAPI_detailConfig_1() {
        HttpResult result = null;

        try {
            final String content = "test";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            Assert.assertTrue(ret);

            List<String> params = Arrays.asList("dataId", dataId, "group", group, "show", "all");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH, null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            Assert.assertEquals(content, JacksonUtils.toObj(result.content).get("content").textValue());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_catalog信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 3*TIME_OUT)
    public void nacos_openAPI_catalog() {
        HttpResult result = null;

        try {
            final String content = "test";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            Assert.assertTrue(ret);

            List<String> params = Arrays.asList("dataId", dataId, "group", group);
            result = agent.httpGet(CONFIG_CONTROLLER_PATH+"/catalog", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            System.out.println(result.content);
            Assert.assertFalse(JacksonUtils.toObj(result.content).get("data").isNull());

        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_queryBeta信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 3*TIME_OUT)
    public void nacos_openAPI_queryBeta_1() {
        HttpResult result = null;

        try {
            final String content = "test-beta";
            List<String> headers = Arrays.asList("betaIps", "127.0.0.1");
            List<String> params1 = Arrays.asList("dataId", dataId, "group", group, "content", content);
            result = agent.httpPost(CONFIG_CONTROLLER_PATH + "/", headers, params1, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertEquals("true", result.content);

            List<String> params = Arrays.asList("dataId", dataId, "group", group, "beta", "true");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertEquals(content, JacksonUtils.toObj(result.content).get("data").get("content").textValue());
            // delete data
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_queryBeta删除信息
     * @TestStep : 1. 发布配置
     *             2. 删除Beta配置信息
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 3*TIME_OUT)
    public void nacos_openAPI_queryBeta_delete() {
        HttpResult result = null;

        try {
            final String content = "test-beta";
            List<String> headers = Arrays.asList("betaIps", "127.0.0.1");
            List<String> params1 = Arrays.asList("dataId", dataId, "group", group, "content", content);
            result = agent.httpPost(CONFIG_CONTROLLER_PATH + "/", headers, params1, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertEquals("true", result.content);


            List<String> params = Arrays.asList("dataId", dataId, "group", group, "beta", "true");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);

            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertTrue(JacksonUtils.toObj(result.content).get("data").booleanValue());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_模糊查询配置信息
     * @TestStep : 1. 发布配置
     *             2. 模糊查询
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_openAPI_fuzzySearchConfig() {
        HttpResult result = null;

        try {
            final String content = "test123";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            Assert.assertTrue(ret);

            List<String> params = Arrays.asList("dataId", dataId, "group", group, "pageNo","1", "pageSize","10", "search", "blur");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);

            Assert.assertTrue(JacksonUtils.toObj(result.content).get("totalCount").intValue() >= 1);
            Assert.assertTrue(JacksonUtils.toObj(result.content).get("pageItems").get(0).get("content").textValue().startsWith(content));
        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_模糊查询配置信息
     * @TestStep : 1. 发布配置
     *             2. 查询配置信息
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_openAPI_fuzzySearchConfig_1() {
        HttpResult result = null;

        try {
            final String content = "test123";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            Assert.assertTrue(ret);

            List<String> params = Arrays.asList("dataId", dataId+"*", "group", group+"*", "pageNo","1", "pageSize","10", "search", "blur");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);

            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertTrue(JacksonUtils.toObj(result.content).get("totalCount").intValue() >= 1);
            Assert.assertEquals(content, JacksonUtils.toObj(result.content).get("pageItems").get(0).get("content").textValue());

        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_查询配置信息
     * @TestStep : 1. 发布配置
     *             2. 查询配置信息
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_openAPI_searchConfig() {
        HttpResult result = null;

        try {
            final String content = "test123";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Assert.assertTrue(ret);
            Thread.sleep(TIME_OUT);

            List<String> params = Arrays.asList("dataId", dataId, "group", group, "pageNo","1", "pageSize","10", "search", "accurate");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);

            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertEquals(1, JacksonUtils.toObj(result.content).get("totalCount").intValue());
            Assert.assertEquals(content, JacksonUtils.toObj(result.content).get("pageItems").get(0).get("content").textValue());

        } catch (Exception e) {
            Assert.fail();
        }
    }

    /**
     * @TCDescription : nacos_openAPI_查询配置信息，包含中文，utf-8
     * @TestStep : 1. 发布配置
     *             2. 查询配置信息
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_openAPI_searchConfig_2() {
        HttpResult result = null;

        try {
            final String content = "test测试";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Assert.assertTrue(ret);
            Thread.sleep(TIME_OUT);

            List<String> params = Arrays.asList("dataId", dataId, "group", group, "pageNo","1", "pageSize","10", "search", "accurate");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, "utf-8", TIME_OUT);
            Assert.assertEquals(HttpURLConnection.HTTP_OK, result.code);
            Assert.assertEquals(1, JacksonUtils.toObj(result.content).get("totalCount").intValue());
            Assert.assertEquals(content, JacksonUtils.toObj(result.content).get("pageItems").get(0).get("content").textValue());
        } catch (Exception e) {
            Assert.fail();
        }
    }

}
