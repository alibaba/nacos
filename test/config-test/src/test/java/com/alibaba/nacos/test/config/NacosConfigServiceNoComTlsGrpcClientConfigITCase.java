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
import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import com.alibaba.nacos.common.remote.client.RpcConstants;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Use configPublishRequest for communication verification between client and server.
 *
 * @author githubcheng2978.
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Nacos.class}, properties = {"nacos.standalone=true", RpcConstants.NACOS_SERVER_RPC + ".enableTls=true",
        RpcConstants.NACOS_SERVER_RPC + ".compatibility=false", RpcConstants.NACOS_SERVER_RPC + ".certChainFile=test-server-cert.pem",
        RpcConstants.NACOS_SERVER_RPC + ".certPrivateKey=test-server-key.pem"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class NacosConfigServiceNoComTlsGrpcClientConfigITCase {
    
    public static AtomicInteger increment = new AtomicInteger(100);
    
    @BeforeAll
    static void beforeClass() throws IOException {
        ConfigCleanUtils.changeToNewTestNacosHome(NacosConfigServiceNoComTlsGrpcClientConfigITCase.class.getSimpleName());
    }
    
    @BeforeAll
    @AfterAll
    static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }
    
    @Test
    @Disabled("TODO, Fix cert expired problem")
    void testTlsServerAndTlsClient() throws Exception {
        Properties properties = new Properties();
        properties.put(RpcConstants.RPC_CLIENT_TLS_ENABLE, "true");
        properties.put(RpcConstants.RPC_CLIENT_TLS_PROVIDER, "openssl");
        properties.put(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH, "test-ca-cert.pem");
        properties.put("serverAddr", "127.0.0.1");
        ConfigService configService = new NacosConfigService(properties);
        String content = UUID.randomUUID().toString();
        String dataId = "test-group" + increment.getAndIncrement();
        String groupId = "test-data" + increment.getAndIncrement();
        boolean b = configService.publishConfig("test-group" + increment.getAndIncrement(), "test-data" + increment.getAndIncrement(),
                content);
        CountDownLatch latch = new CountDownLatch(1);
        configService.addListener(dataId, groupId, new AbstractConfigChangeListener() {
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
        latch.await(5, TimeUnit.SECONDS);
        assertTrue(b);
    }
    
    @Test
    void testTlsServerAndPlainClient() throws Exception {
        Properties propertiesfalse = new Properties();
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_ENABLE, "false");
        propertiesfalse.put("serverAddr", "127.0.0.1");
        ConfigService configServiceFalse = new NacosConfigService(propertiesfalse);
        String dataId = "test-group" + increment.getAndIncrement();
        String groupId = "test-data" + increment.getAndIncrement();
        String content = UUID.randomUUID().toString();
        boolean res = configServiceFalse.publishConfig(dataId, groupId, content);
        CountDownLatch latch2 = new CountDownLatch(1);
        configServiceFalse.addListener(dataId, groupId, new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                ConfigChangeItem cci = event.getChangeItem("content");
                System.out.println("content:" + cci);
                if (!content.equals(cci.getNewValue())) {
                    return;
                }
                latch2.countDown();
            }
        });
        latch2.await(5, TimeUnit.SECONDS);
        assertFalse(res);
    }
}
