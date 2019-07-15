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
package com.alibaba.nacos.naming.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nkorange
 * @author jifengnan 2019-07-13
 */
public class InstanceTest extends BaseTest {

    private Instance instance;

    @Test
    public void testCreateInstance() {
        instance = createInstance(IP1, 1234);
        instance.setWeight(5);

        Assert.assertEquals(IP1, instance.getIp());
        Assert.assertEquals(1234, instance.getPort());
        Assert.assertEquals(5, instance.getWeight(), 0.001);
        Assert.assertEquals(TEST_CLUSTER_NAME, instance.getClusterName());
        Assert.assertEquals(TEST_SERVICE_NAME, instance.getServiceName());

        String hostName = "www.nacos.com";
        instance = createInstance(hostName, 1234);
        Assert.assertEquals(hostName, instance.getIp());

        Instance result = JSON.parseObject(OLD_DATA, Instance.class);
        Assert.assertEquals(IP1, result.getIp());
        Assert.assertEquals(1, result.getPort());
        Assert.assertEquals(2, result.getWeight(), 0.001);
        Assert.assertEquals(TEST_CLUSTER_NAME, result.getClusterName());
        Assert.assertEquals(TEST_SERVICE_NAME, result.getServiceName());

        result = JSON.parseObject(NEW_DATA, Instance.class);
        Assert.assertEquals(hostName, result.getIp());
        Assert.assertEquals(1234, result.getPort());
        Assert.assertEquals(instance.getCluster(), result.getCluster());
        Assert.assertEquals(TEST_CLUSTER_NAME, result.getClusterName());
        Assert.assertEquals(TEST_SERVICE_NAME, result.getServiceName());
    }

    @Test
    public void testCreateInstance_IllegalIp() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("IP(1.1) or port(1234) format is invalid");
        instance = createInstance("1.1", 1234);
    }

    @Test
    public void testCreateInstance_IllegalPort() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Port must be greater than or equals to 0");
        instance = createInstance(IP1, -1);
    }

    @Test
    public void testValidate_IllegalWeight() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Illegal weight value: 10000.1");
        instance = createInstance(IP1, 1234);
        instance.setWeight(10000.1);
        instance.validate();
    }

    @Test
    public void rsInfo() {

        RsInfo info = new RsInfo();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "2222");
        info.setMetadata(metadata);
        System.out.println(JSON.toJSONString(info));

        String json = JSON.toJSONString(info);
        RsInfo info1 = JSON.parseObject(json, RsInfo.class);
        System.out.println(info1);
    }

    static final String OLD_DATA = "{\"clusterName\":\"test-cluster\",\"enabled\":true,\"ephemeral\":true,\"healthy\":true,\"instanceHeartBeatInterval\":6000,\"instanceHeartBeatTimeOut\":16000,\"ip\":\"1.1.1.1\",\"ipDeleteTimeout\":30000,\"lastBeat\":1563028915804,\"marked\":false,\"metadata\":{},\"port\":1,\"serviceName\":\"test-service\",\"weight\":2.0}";
    private static final String NEW_DATA = "{\"cluster\":{\"defCkport\":80,\"defIPPort\":80,\"defaultCheckPort\":80,\"defaultPort\":80,\"healthCheckTask\":{\"cancelled\":false,\"checkRTBest\":9223372036854775807,\"checkRTLast\":-1,\"checkRTLastLast\":-1,\"checkRTNormalized\":5088,\"checkRTWorst\":0,\"cluster\":{\"$ref\":\"..\"},\"startTime\":1563081741738},\"healthChecker\":{\"type\":\"TCP\"},\"metadata\":{},\"name\":\"test-cluster\",\"service\":{\"checksum\":\"b2f1d9484f316186e1b5f746a22ecdc3\",\"clusterMap\":{\"test-cluster\":{\"$ref\":\"$.cluster\"}},\"enabled\":true,\"groupName\":\"DEFAULT_GROUP\",\"ipDeleteTimeout\":30000,\"lastModifiedMillis\":0,\"metadata\":{},\"name\":\"test-service\",\"namespaceId\":\"public\",\"owners\":[],\"protectThreshold\":0.0,\"resetWeight\":false,\"selector\":{\"type\":\"none\"}},\"serviceName\":\"test-service\",\"sitegroup\":\"\",\"useIPPort4Check\":true},\"clusterName\":\"test-cluster\",\"enabled\":true,\"ephemeral\":true,\"healthy\":true,\"instanceHeartBeatInterval\":5000,\"instanceHeartBeatTimeOut\":15000,\"ip\":\"www.nacos.com\",\"ipDeleteTimeout\":30000,\"lastBeat\":1563081741738,\"marked\":false,\"metadata\":{},\"port\":1234,\"serviceName\":\"test-service\",\"weight\":1.0}";
}
