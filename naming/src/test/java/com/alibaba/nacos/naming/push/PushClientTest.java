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
package com.alibaba.nacos.naming.push;


import com.alibaba.nacos.naming.BaseTest;
import org.junit.Test;

import java.net.InetSocketAddress;

/**
 * @author nkorange
 */
public class PushClientTest extends BaseTest  {

    @Test
    public void testAddClient() {

        String namespaceId = "public";
        String serviceName = "test.1";
        String clusters = "DEFAULT";
        String agent = "Nacos-Java-Client:v1.1.4";
        String clientIp = "xxxxx";
        String app = "nacos";
        int udpPort = 10000;


        pushService.addClient(namespaceId
            , serviceName
            , clusters
            , agent
            , new InetSocketAddress(clientIp, udpPort)
            , null
            , namespaceId
            , app);
    }
}
