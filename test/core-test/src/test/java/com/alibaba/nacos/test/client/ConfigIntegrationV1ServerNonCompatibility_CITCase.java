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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
@ExtendWith(SpringExtension.class)
@TestConfiguration
@SpringBootTest(classes = {Nacos.class}, properties = {"server.servlet.context-path=/nacos",
        RpcConstants.NACOS_SERVER_RPC + ".compatibility=false", RpcConstants.NACOS_SERVER_RPC + ".enableTls=true",
        RpcConstants.NACOS_SERVER_RPC + ".certChainFile=test-server-cert.pem",
        RpcConstants.NACOS_SERVER_RPC + ".certPrivateKey=test-server-key.pem"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Disabled("TODO, Fix cert expired problem")
public class ConfigIntegrationV1ServerNonCompatibility_CITCase {
    
    public static AtomicInteger increment = new AtomicInteger(100);
    
    @LocalServerPort
    private int port;
    
    @BeforeAll
    static void beforeClass() throws IOException {
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigIntegrationV1ServerNonCompatibility_CITCase.class.getSimpleName());
    }
    
    @BeforeAll
    @AfterAll
    static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
    }
    
    @Test
    void test_a_TlsServer() throws Exception {
        RpcClient client = RpcClientFactory.createClient("testTlsServer", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), null);
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        serverInfo.setServerIp("127.0.0.1");
        serverInfo.setServerPort(port);
        
        Connection connection = client.connectToServer(serverInfo);
        assertNull(connection);
    }
    
    @Test
    void test_b_ServerTlsTrustAll() throws Exception {
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(true);
        tlsConfig.setTrustAll(true);
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        serverInfo.setServerIp("127.0.0.1");
        serverInfo.setServerPort(port);
        
        RpcClient clientTrustCa = RpcClientFactory.createClient("testServerTlsTrustCa", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), tlsConfig);
        Connection connectionTrustCa = clientTrustCa.connectToServer(serverInfo);
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        String content = UUID.randomUUID().toString();
        configPublishRequest.setContent(content);
        configPublishRequest.setGroup("test-group" + increment.getAndIncrement());
        configPublishRequest.setDataId("test-data" + increment.getAndIncrement());
        
        Response response = connectionTrustCa.request(configPublishRequest, TimeUnit.SECONDS.toMillis(3));
        assertTrue(response.isSuccess());
        connectionTrustCa.close();
    }
    
    @Test
    void test_c_ServerTlsTrustCa() throws Exception {
        
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        serverInfo.setServerIp("127.0.0.1");
        serverInfo.setServerPort(port);
        
        RpcClientTlsConfig tlsConfig = new RpcClientTlsConfig();
        tlsConfig.setEnableTls(true);
        tlsConfig.setTrustCollectionCertFile("test-ca-cert.pem");
        RpcClient clientTrustCa = RpcClientFactory.createClient("testServerTlsTrustCa", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), tlsConfig);
        Connection connectionTrustCa = clientTrustCa.connectToServer(serverInfo);
        ConfigPublishRequest configPublishRequestCa = new ConfigPublishRequest();
        String contentCa = UUID.randomUUID().toString();
        
        configPublishRequestCa.setContent(contentCa);
        configPublishRequestCa.setGroup("test-group" + increment.getAndIncrement());
        configPublishRequestCa.setDataId("test-data" + increment.getAndIncrement());
        Response responseCa = connectionTrustCa.request(configPublishRequestCa, TimeUnit.SECONDS.toMillis(3));
        assertTrue(responseCa.isSuccess());
        connectionTrustCa.close();
    }
}
