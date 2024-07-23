/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.plugin.encryption.EncryptionPluginManager;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

abstract class AbstractConfigAPI_CITCase {
    
    public static final long TIME_OUT = 5000;
    
    private static final String CONFIG_CONTROLLER_PATH = "/v1/cs/configs";
    
    private static final String SPECIAL_CHARACTERS = "!@#$%^&*()_+-=_|/'?.";
    
    static ConfigService iconfig = null;
    
    static HttpAgent agent = null;
    
    private static String dataId = "yanlin";
    
    private static String group = "yanlin";
    
    @Value("${server.servlet.context-path}")
    private String contextPath;
    
    @LocalServerPort
    private int port;
    
    private EncryptionPluginService mockEncryptionPluginService;
    
    @BeforeEach
    public void initEncryptionPluginService() {
        mockEncryptionPluginService = new EncryptionPluginService() {
            
            private static final String ALGORITHM = "AES";
            
            private static final String AES_PKCS5P = "AES/ECB/PKCS5Padding";
            
            // 随机生成密钥-用来加密数据内容
            private final String contentKey = generateKey();
            
            // 随机生成密钥-用来加密密钥
            private final String theKeyOfContentKey = generateKey();
            
            private String generateKey() {
                SecureRandom secureRandom = new SecureRandom();
                KeyGenerator keyGenerator;
                try {
                    keyGenerator = KeyGenerator.getInstance(ALGORITHM);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
                keyGenerator.init(128, secureRandom);
                SecretKey secretKey = keyGenerator.generateKey();
                byte[] keyBytes = secretKey.getEncoded();
                return Base64.encodeBase64String(keyBytes);
            }
            
            @Override
            public String encrypt(String secretKey, String content) {
                return Base64.encodeBase64String(aes(Cipher.ENCRYPT_MODE, content, secretKey));
            }
            
            @Override
            public String decrypt(String secretKey, String content) {
                if (StringUtils.isBlank(secretKey)) {
                    return null;
                }
                return aesDecrypt(content, secretKey);
            }
            
            @Override
            public String generateSecretKey() {
                return contentKey;
            }
            
            @Override
            public String algorithmName() {
                return ALGORITHM.toLowerCase();
            }
            
            @Override
            public String encryptSecretKey(String secretKey) {
                return Base64.encodeBase64String(aes(Cipher.ENCRYPT_MODE, generateSecretKey(), theKeyOfContentKey));
            }
            
            @Override
            public String decryptSecretKey(String secretKey) {
                if (StringUtils.isBlank(secretKey)) {
                    return null;
                }
                return aesDecrypt(secretKey, theKeyOfContentKey);
            }
            
            private byte[] aes(int mode, String content, String key) {
                try {
                    return aesBytes(mode, content.getBytes(StandardCharsets.UTF_8), key);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            private byte[] aesBytes(int mode, byte[] content, String key) {
                SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
                Cipher cipher = null;
                try {
                    cipher = Cipher.getInstance(AES_PKCS5P);
                    cipher.init(mode, keySpec);
                    return cipher.doFinal(content);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            private String aesDecrypt(String content, String key) {
                byte[] bytes = aesBytes(Cipher.DECRYPT_MODE, Base64.decodeBase64(content), key);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        };
        EncryptionPluginManager.join(mockEncryptionPluginService);
    }
    
    @BeforeEach
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        properties.put(PropertyKeyConst.CONTEXT_PATH, contextPath);
        if (null == iconfig) {
            iconfig = NacosFactory.createConfigService(properties);
        }
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();
    }
    
    @AfterEach
    public void cleanup() throws Exception {
        HttpRestResult<String> result = null;
        try {
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("beta", "true");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertTrue(JacksonUtils.toObj(result.getData()).get("data").booleanValue());
            assertTrue(JacksonUtils.toObj(result.getData()).get("data").booleanValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    /**
     * @TCDescription : nacos_正常获取数据
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_getconfig_1() throws Exception {
        final String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content, value);
        result = iconfig.removeConfig(dataId, group);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        value = iconfig.getConfig(dataId, group, TIME_OUT);
        System.out.println(value);
        assertNull(value);
    }
    
    /**
     * @TCDescription : nacos_正常推送&获取加密数据
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacosPublishAndGetConfig() throws Exception {
        String dataId = "cipher-aes-dataId";
        final String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content, value);
        result = iconfig.removeConfig(dataId, group);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertNull(value);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_服务端无配置时，获取配置
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_getconfig_2() throws Exception {
        String content = iconfig.getConfig(dataId, "nacos", TIME_OUT);
        assertNull(content);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_获取配置时dataId为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_getconfig_3() throws Exception {
        try {
            String content = iconfig.getConfig(null, group, TIME_OUT);
        } catch (Exception e) {
            assertTrue(true);
            return;
        }
        fail();
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_获取配置时group为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_getconfig_4() throws Exception {
        final String dataId = "nacos_getconfig_4";
        final String content = "test";
        boolean result = iconfig.publishConfig(dataId, null, content);
        assertTrue(result);
        Thread.sleep(TIME_OUT);
        
        String value = iconfig.getConfig(dataId, null, TIME_OUT);
        assertEquals(content, value);
        
        result = iconfig.removeConfig(dataId, null);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_服务端无该配置项时，正常创建配置
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_1() throws Exception {
        final String content = "publishConfigTest";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        result = iconfig.removeConfig(dataId, group);
        assertTrue(result);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_服务端有该配置项时，正常修改配置
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_2() throws Exception {
        final String content = "publishConfigTest";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        
        final String content1 = "test.abc";
        result = iconfig.publishConfig(dataId, group, content1);
        Thread.sleep(TIME_OUT);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content1, value);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_发布配置时包含特殊字符
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_3() throws Exception {
        String content = "test" + SPECIAL_CHARACTERS;
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content, value);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_发布配置时dataId为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_4() throws Exception {
        try {
            String content = "test";
            boolean result = iconfig.publishConfig(null, group, content);
            Thread.sleep(TIME_OUT);
            assertTrue(result);
        } catch (Exception e) {
            assertTrue(true);
            return;
        }
        fail();
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_发布配置时group为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_5() throws Exception {
        final String dataId = "nacos_publishConfig_5";
        String content = "test";
        boolean result = iconfig.publishConfig(dataId, null, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        
        String value = iconfig.getConfig(dataId, null, TIME_OUT);
        assertEquals(content, value);
    }
    
    
    /**
     * @throws Exception
     * @TCDescription : nacos_发布配置时配置内容为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_6() throws Exception {
        String content = null;
        try {
            boolean result = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
        } catch (Exception e) {
            assertTrue(true);
            return;
        }
        fail();
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_发布配置时配置内容包含中文字符
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_publishConfig_7() throws Exception {
        String content = "阿里abc";
        boolean result = iconfig.publishConfig(dataId, group, content);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertEquals(content, value);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_服务端有该配置项时，正常删除配置
     */
    @Test
    public void nacos_removeConfig_1() throws Exception {
        String content = "test";
        boolean result = iconfig.publishConfig(dataId, group, content);
        
        assertTrue(result);
        Thread.sleep(TIME_OUT);
        
        result = iconfig.removeConfig(dataId, group);
        assertTrue(result);
        Thread.sleep(TIME_OUT);
        String value = iconfig.getConfig(dataId, group, TIME_OUT);
        assertNull(value);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_服务端无该配置项时，配置删除失败
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_removeConfig_2() throws Exception {
        group += "removeConfig2";
        boolean result = iconfig.removeConfig(dataId, group);
        assertTrue(result);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_删除配置时dataId为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_removeConfig_3() throws Exception {
        try {
            boolean result = iconfig.removeConfig(null, group);
            assertTrue(result);
        } catch (Exception e) {
            assertTrue(true);
            return;
        }
        fail();
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_删除配置时group为null
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_removeConfig_4() throws Exception {
        boolean result = iconfig.removeConfig(dataId, null);
        assertTrue(result);
    }
    
    /**
     * @throws Exception
     * @TCDescription : nacos_添加对dataId的监听，在服务端修改配置后，获取监听后的修改的配置
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_addListener_1() throws Exception {
        final AtomicInteger count = new AtomicInteger(0);
        final String content = "test-abc";
        boolean result = iconfig.publishConfig(dataId, group, content);
        assertTrue(result);
        
        Listener ml = new Listener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("receive23:" + configInfo);
                count.incrementAndGet();
                assertEquals(content, configInfo);
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
        assertTrue(count.get() >= 1);
        iconfig.removeListener(dataId, group, ml);
    }
    
    /**
     * @TCDescription : nacos_设置监听器为null，抛出异常信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_addListener_2() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            iconfig.addListener(dataId, group, null);
        });
    }
    
    
    /**
     * @TCDescription : nacos_添加对dataId的监听，修改服务端配置，正常推送并只推送一次
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = Constants.CONFIG_LONG_POLL_TIMEOUT << 2, unit = TimeUnit.MILLISECONDS)
    public void nacos_addListener_3() throws InterruptedException, NacosException {
        final AtomicInteger count = new AtomicInteger(0);
        final String dataId = "nacos_addListener_3";
        final String group = "nacos_addListener_3";
        final String content = "test-abc-" + System.currentTimeMillis();
        final String newContent = "nacos_addListener_3-" + System.currentTimeMillis();
        boolean result = iconfig.publishConfig(dataId, group, content);
        assertTrue(result);
        
        // Maximum assurance level notification has been performed
        ThreadUtils.sleep(5000);
        
        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
                assertEquals(newContent, configInfo);
            }
        };
        String receive = iconfig.getConfigAndSignListener(dataId, group, 5000L, ml);
        assertEquals(content, receive);
        result = iconfig.publishConfig(dataId, group, newContent);
        assertTrue(result);
        // Get enough sleep to ensure that the monitor is triggered only once
        // during the two long training sessions
        ThreadUtils.sleep(Constants.CONFIG_LONG_POLL_TIMEOUT << 1);
        assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }
    
    /**
     * @TCDescription : nacos_服务端无配置时，添加对dataId的监听
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
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
        assertTrue(result);
        
        while (count.get() == 0) {
            Thread.sleep(3000);
        }
        assertEquals(1, count.get());
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
        assertTrue(result);
        
        Thread.sleep(2000);
        
        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
                assertEquals(newContent, configInfo);
            }
        };
        
        String receiveContent = iconfig.getConfigAndSignListener(dataId, group, 1000, ml);
        System.out.println(receiveContent);
        
        result = iconfig.publishConfig(dataId, group, newContent);
        assertTrue(result);
        
        assertEquals(content, receiveContent);
        Thread.sleep(2000);
        
        assertEquals(1, count.get());
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
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        properties.put(PropertyKeyConst.ENABLE_REMOTE_SYNC_CONFIG, "true");
        properties.put(PropertyKeyConst.CONTEXT_PATH, contextPath);
        ConfigService iconfig = NacosFactory.createConfigService(properties);
        
        final AtomicInteger count = new AtomicInteger(0);
        final String dataId = "nacos_addListener_6";
        final String group = "nacos_addListener_6";
        final String content = "test-abc";
        final String newContent = "new-test-def";
        boolean result = iconfig.publishConfig(dataId, group, content);
        assertTrue(result);
        
        Thread.sleep(2000);
        
        Listener ml = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                count.incrementAndGet();
                System.out.println("Listener receive : [" + configInfo + "]");
                assertEquals(newContent, configInfo);
            }
        };
        
        iconfig.addListener(dataId, group, ml);
        
        String receiveContent = iconfig.getConfig(dataId, group, 1000);
        
        System.out.println(receiveContent);
        
        result = iconfig.publishConfig(dataId, group, newContent);
        assertTrue(result);
        
        Thread.sleep(2000);
        
        receiveContent = iconfig.getConfig(dataId, group, 1000);
        
        assertEquals(newContent, receiveContent);
        
        assertEquals(1, count.get());
        iconfig.removeListener(dataId, group, ml);
    }
    
    /**
     * @TCDescription : nacos_正常移除监听器
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_removeListener_1() throws Exception {
        iconfig.addListener(dataId, group, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                fail();
            }
        });
        Thread.sleep(TIME_OUT);
        try {
            iconfig.removeListener(dataId, group, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println("remove receive:" + configInfo);
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
    @Test
    @Timeout(value = TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_removeListener_2() {
        group += "test.nacos";
        Assertions.assertDoesNotThrow(() -> {
            iconfig.removeListener(dataId, group, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                
                }
            });
        });
    }
    
    /**
     * @TCDescription : nacos_存在多个监听器时，删除最后一个监听器
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
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
                //System.out.println("ml1 remove listener receive:" + configInfo);
                count.incrementAndGet();
                assertEquals(contentRemove, configInfo);
            }
        };
        iconfig.addListener(dataId, group, ml);
        iconfig.addListener(dataId, group, ml1);
        
        iconfig.removeListener(dataId, group, ml);
        Thread.sleep(TIME_OUT);
        
        boolean result = iconfig.publishConfig(dataId, group, contentRemove);
        Thread.sleep(TIME_OUT);
        assertTrue(result);
        
        while (count.get() == 0) {
            Thread.sleep(3000);
        }
        assertNotEquals(0, count.get());
    }
    
    /**
     * @TCDescription : nacos_监听器为null时
     * @TestStep : TODO Test steps
     * @ExpectResult : TODO expect results
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_removeListener_4() {
        assertThrows(IllegalArgumentException.class, () -> {
            iconfig.removeListener(dataId, group, null);
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_配置具体信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_detailConfig_1() {
        Assertions.assertDoesNotThrow(() -> {
            final String content = "test";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            assertTrue(ret);
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("show", "all");
            HttpRestResult<String> result = agent.httpGet(CONFIG_CONTROLLER_PATH, null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            assertEquals(content, JacksonUtils.toObj(result.getData()).get("content").textValue());
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_catalog信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_catalog() {
        Assertions.assertDoesNotThrow(() -> {
            final String content = "test";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            assertTrue(ret);
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            HttpRestResult<String> result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/catalog", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            System.out.println(result.getData());
            assertFalse(JacksonUtils.toObj(result.getData()).get("data").isNull());
            
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_queryBeta信息
     * @TestStep :
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_queryBeta_1() {
        Assertions.assertDoesNotThrow(() -> {
            HttpRestResult<String> result = null;
            final String content = "test-beta";
            Map<String, String> headers = new HashMap<>();
            headers.put("betaIps", "127.0.0.1");
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("content", content);
            result = agent.httpPost(CONFIG_CONTROLLER_PATH + "/", headers, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertEquals("true", result.getData());
            params.clear();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("beta", "true");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertEquals(content, JacksonUtils.toObj(result.getData()).get("data").get("content").textValue());
            // delete data
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_queryBeta删除信息
     * @TestStep : 1. 发布配置 2. 删除Beta配置信息
     * @ExpectResult :
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 3 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_queryBeta_delete() {
        HttpRestResult<String> result = null;
        
        try {
            final String content = "test-beta";
            Map<String, String> headers = new HashMap<>();
            headers.put("betaIps", "127.0.0.1");
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("content", content);
            result = agent.httpPost(CONFIG_CONTROLLER_PATH + "/", headers, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertEquals("true", result.getData());
            
            params.clear();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("beta", "true");
            result = agent.httpDelete(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertTrue(JacksonUtils.toObj(result.getData()).get("data").booleanValue());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
    
    /**
     * @TCDescription : nacos_openAPI_模糊查询配置信息
     * @TestStep : 1. 发布配置 2. 模糊查询
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_fuzzySearchConfig() {
        Assertions.assertDoesNotThrow(() -> {
            HttpRestResult<String> result;
            final String content = "test123";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            assertTrue(ret);
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("pageNo", "1");
            params.put("pageSize", "10");
            params.put("search", "blur");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            
            assertTrue(JacksonUtils.toObj(result.getData()).get("totalCount").intValue() >= 1);
            assertTrue(JacksonUtils.toObj(result.getData()).get("pageItems").get(0).get("content").textValue().startsWith(content));
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_模糊查询配置信息
     * @TestStep : 1. 发布配置 2. 查询配置信息
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_fuzzySearchConfig_1() {
        
        Assertions.assertDoesNotThrow(() -> {
            HttpRestResult<String> result;
            final String content = "test123";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            Thread.sleep(TIME_OUT);
            assertTrue(ret);
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId + "*");
            params.put("group", group + "*");
            params.put("pageNo", "1");
            params.put("pageSize", "10");
            params.put("search", "blur");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertTrue(JacksonUtils.toObj(result.getData()).get("totalCount").intValue() >= 1);
            assertEquals(content, JacksonUtils.toObj(result.getData()).get("pageItems").get(0).get("content").textValue());
            
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_查询配置信息
     * @TestStep : 1. 发布配置 2. 查询配置信息
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_searchConfig() {
        
        Assertions.assertDoesNotThrow(() -> {
            HttpRestResult<String> result;
            final String content = "test123";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            assertTrue(ret);
            Thread.sleep(TIME_OUT);
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("pageNo", "1");
            params.put("pageSize", "10");
            params.put("search", "accurate");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertEquals(1, JacksonUtils.toObj(result.getData()).get("totalCount").intValue());
            assertEquals(content, JacksonUtils.toObj(result.getData()).get("pageItems").get(0).get("content").textValue());
            
        });
    }
    
    /**
     * @TCDescription : nacos_openAPI_查询配置信息，包含中文，utf-8
     * @TestStep : 1. 发布配置 2. 查询配置信息
     * @ExpectResult : 获取查询到配置
     * @author xiaochun.xxc
     * @since 3.6.8
     */
    @Test
    @Timeout(value = 5 * TIME_OUT, unit = TimeUnit.MILLISECONDS)
    public void nacos_openAPI_searchConfig_2() {
        
        Assertions.assertDoesNotThrow(() -> {
            HttpRestResult<String> result;
            final String content = "test测试";
            boolean ret = iconfig.publishConfig(dataId, group, content);
            assertTrue(ret);
            Thread.sleep(TIME_OUT);
            
            Map<String, String> params = new HashMap<>();
            params.put("dataId", dataId);
            params.put("group", group);
            params.put("pageNo", "1");
            params.put("pageSize", "10");
            params.put("search", "accurate");
            result = agent.httpGet(CONFIG_CONTROLLER_PATH + "/", null, params, agent.getEncode(), TIME_OUT);
            assertEquals(HttpURLConnection.HTTP_OK, result.getCode());
            assertEquals(1, JacksonUtils.toObj(result.getData()).get("totalCount").intValue());
            assertEquals(content, JacksonUtils.toObj(result.getData()).get("pageItems").get(0).get("content").textValue());
        });
    }
    
    
}
