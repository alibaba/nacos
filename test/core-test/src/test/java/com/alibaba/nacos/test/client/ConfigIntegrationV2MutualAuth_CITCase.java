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


package com.alibaba.nacos.test.client;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import com.alibaba.nacos.common.remote.client.RpcConstants;
import com.alibaba.nacos.test.ConfigCleanUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * use  configPublishRequest for  communication verification between client and server.
 *
 * @author githubcheng2978
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Nacos.class}, properties = {"nacos.standalone=true", RpcConstants.NACOS_SERVER_RPC + ".mutualAuthEnable=true",
        RpcConstants.NACOS_SERVER_RPC + ".compatibility=false", RpcConstants.NACOS_SERVER_RPC + ".enableTls=true",
        RpcConstants.NACOS_SERVER_RPC + ".certChainFile=test-server-cert.pem",
        RpcConstants.NACOS_SERVER_RPC + ".certPrivateKey=test-server-key.pem", RpcConstants.NACOS_SERVER_RPC
        + ".trustCollectionCertFile=test-ca-cert.pem"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigIntegrationV2MutualAuth_CITCase {
    
    public static AtomicInteger increment = new AtomicInteger(100);
    
    @LocalServerPort
    private int port;
    
    @BeforeAll
    static void beforeClass() throws IOException {
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigIntegrationV2MutualAuth_CITCase.class.getSimpleName());
        
    }
    
    @AfterEach
    void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }
    
    @Test
    @Disabled("TODO, fix the cert expired problem")
    void test_d_MutualAuth() throws Exception {
        
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(true);
        tlsConfig.setMutualAuthEnable(true);
        tlsConfig.setCertChainFile("test-client-cert.pem");
        tlsConfig.setCertPrivateKey("test-client-key.pem");
        tlsConfig.setTrustCollectionCertFile("test-ca-cert.pem");
        RpcClient client = RpcClientFactory.createClient("testMutualAuth", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), tlsConfig);
        
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        serverInfo.setServerIp("127.0.0.1");
        serverInfo.setServerPort(port);
        
        Connection connection = client.connectToServer(serverInfo);
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        
        String content = UUID.randomUUID().toString();
        
        configPublishRequest.setContent(content);
        configPublishRequest.setGroup("test-group" + increment.getAndIncrement());
        configPublishRequest.setDataId("test-data" + increment.getAndIncrement());
        configPublishRequest.setRequestId(content);
        Response response = connection.request(configPublishRequest, TimeUnit.SECONDS.toMillis(5));
        assertTrue(response.isSuccess());
        connection.close();
    }
    
    @Test
    void test_e_ServerMutualAuthOnly() throws Exception {
        
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(true);
        tlsConfig.setTrustCollectionCertFile("test-ca-cert.pem");
        RpcClient client = RpcClientFactory.createClient("testServerMutualAuthNoly", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), tlsConfig);
        
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        serverInfo.setServerIp("127.0.0.1");
        serverInfo.setServerPort(port);
        Connection connection = client.connectToServer(serverInfo);
        assertNull(connection);
        TimeUnit.SECONDS.sleep(3);
    }
}
