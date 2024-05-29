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
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * use  configPublishRequest for  communication verification between client and server.
 *
 * @author githubcheng2978.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Nacos.class}, properties = {"nacos.standalone=true",
        RpcConstants.NACOS_SERVER_RPC + ".enableTls=true", RpcConstants.NACOS_SERVER_RPC + ".mutualAuthEnable=true",
        RpcConstants.NACOS_SERVER_RPC + ".compatibility=false",
        RpcConstants.NACOS_SERVER_RPC + ".certChainFile=test-server-cert.pem",
        RpcConstants.NACOS_SERVER_RPC + ".certPrivateKey=test-server-key.pem", RpcConstants.NACOS_SERVER_RPC
        + ".trustCollectionCertFile=test-ca-cert.pem"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class NacosConfigV2MutualAuth_CITCase {
    
    
    public static AtomicInteger increment = new AtomicInteger(100);
    
    @BeforeClass
    public static void beforeClass() throws IOException {
        ConfigCleanUtils.changeToNewTestNacosHome(NacosConfigV2MutualAuth_CITCase.class.getSimpleName());
        
    }
    
    @After
    public void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }
    
    @Test
    @Ignore("TODO, Fix cert expired problem")
    public void test_d_MutualAuth() throws Exception {
        Properties propertiesfalse = new Properties();
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_ENABLE, "true");
        propertiesfalse.put(RpcConstants.RPC_CLIENT_MUTUAL_AUTH, "true");
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_CERT_KEY, "test-client-key.pem");
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH, "test-ca-cert.pem");
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_CERT_CHAIN_PATH, "test-client-cert.pem");
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
        Assert.assertTrue(res);
    }
    
    @Test
    public void test_d_MutualAuthButClientNot() throws Exception {
        
        Properties propertiesfalse = new Properties();
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_ENABLE, "true");
        propertiesfalse.put(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH, "test-client-cert.pem");
        
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
        Assert.assertFalse(res);
    }
}
