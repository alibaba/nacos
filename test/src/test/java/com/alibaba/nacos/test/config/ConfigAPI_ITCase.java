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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author xiaochun.xxc
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigAPI_ITCase {
    public static final long TIME_OUT = 3000;
    public ConfigService iconfig = null;
    String SPECIAL_CHARACTERS = "!@#$%^&*()_+-=_|/'?.";
    String dataId = "yanlin";
    String group = "yanlin";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Before
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1"+":"+port);
        iconfig = NacosFactory.createConfigService(properties);
    }

    @After
    public void cleanup() throws Exception {
    }

    /**
     * @TCDescription : nacos_正常获取数据
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
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
        Assert.assertEquals(null, value);
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
        Assert.assertTrue(false);
    }

    /**
     * @TCDescription : nacos_获取配置时group为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_getconfig_4() throws Exception {
        final String content = "test";

        boolean result = iconfig.publishConfig(dataId, null, content);
        Thread.sleep(2*TIME_OUT);
        Assert.assertTrue(result);

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
        Assert.assertTrue(false);
    }

    /**
     * @TCDescription : nacos_发布配置时group为null
     * @throws Exception
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_publishConfig_5() throws Exception {
        String content = "test";
        boolean result = iconfig.publishConfig(dataId, null, content);
        Thread.sleep(2*TIME_OUT);
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
        Assert.assertTrue(false);
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
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeConfig_1() throws Exception {
        String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);

        result = iconfig.removeConfig(dataId, group);
        Thread.sleep(TIME_OUT);
        Assert.assertTrue(result);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        Assert.assertEquals(null, value);
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
        Assert.assertTrue(false);
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
                // TODO Auto-generated method stub
                System.out.println("recieve2:" + configInfo);
                count.incrementAndGet();
                Assert.assertEquals(content, configInfo);
            }

            @Override
            public Executor getExecutor() {
                // TODO Auto-generated method stub
                return null;
            }
        };
        iconfig.addListener(dataId, group, ml);
        while (count.get() == 0) {
            Thread.sleep(2000);
        }
        Assert.assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }

    /**
     * @TCDescription : nacos_设置监听器为null，抛出异常信息
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = TIME_OUT)
    public void nacos_addListener_2() {
        try {
            iconfig.addListener(dataId, group, null);
            Assert.assertFalse(true);
        } catch (Exception e) {
            Assert.assertFalse(false);
        }
    }


    /**
     * @TCDescription : nacos_添加对dataId的监听，修改服务端配置，正常推送并只推送一次
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_addListener_3() throws InterruptedException, NacosException {
        final AtomicInteger count = new AtomicInteger(0);
        final String content = "test-abc";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Assert.assertTrue(result);

        Listener ml = new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                // TODO Auto-generated method stub
                count.incrementAndGet();
                Assert.assertEquals(content, configInfo);
            }

            @Override
            public Executor getExecutor() {
                // TODO Auto-generated method stub
                return null;
            }
        };
        iconfig.addListener(dataId, group, ml);
        while (count.get() == 0) {
            Thread.sleep(2000);
        }
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

        Listener ml = new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                // TODO Auto-generated method stub
                count.incrementAndGet();
            }

            @Override
            public Executor getExecutor() {
                // TODO Auto-generated method stub
                return null;
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
     * @TCDescription : nacos_正常移除监听器
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test(timeout = 5*TIME_OUT)
    public void nacos_removeListener_1() throws Exception {
        iconfig.addListener(dataId, group, new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                // TODO Auto-generated method stub
                Assert.assertTrue(false);
            }

            @Override
            public Executor getExecutor() {
                // TODO Auto-generated method stub
                return null;
            }
        });
        Thread.sleep(TIME_OUT);
        try {
            iconfig.removeListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    // TODO Auto-generated method stub
                    System.out.println("remove recieve:" + configInfo);
                }

                @Override
                public Executor getExecutor() {
                    // TODO Auto-generated method stub
                    return null;
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
            iconfig.removeListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    // TODO Auto-generated method stub
                }

                @Override
                public Executor getExecutor() {
                    // TODO Auto-generated method stub
                    return null;
                }
            });
        } catch (Exception e) {
            Assert.assertTrue(false);
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

        Listener ml = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
            }
        };
        Listener ml1 = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

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
        iconfig.removeListener(dataId, group, (Listener) null);
        Assert.assertTrue(true);
    }

}
