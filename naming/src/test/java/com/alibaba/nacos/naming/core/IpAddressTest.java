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
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class IpAddressTest {

    private IpAddress ipAddress;

    @Before
    public void before() {
        ipAddress = new IpAddress();
    }

    @Test
    public void updateIp() {
        ipAddress.setIp("1.1.1.1");
        ipAddress.setPort(1234);
        ipAddress.setWeight(5);

        Assert.assertEquals("1.1.1.1", ipAddress.getIp());
        Assert.assertEquals(1234, ipAddress.getPort());
        Assert.assertEquals(5, ipAddress.getWeight(), 0.001);
    }

    @Test
    public void fromJson() {
        ipAddress = IpAddress.fromJSON("2.2.2.2:8888_2_TEST1");
        Assert.assertEquals("2.2.2.2", ipAddress.getIp());
        Assert.assertEquals(8888, ipAddress.getPort());
        Assert.assertEquals(2, ipAddress.getWeight(), 0.001);
        Assert.assertEquals("TEST1", ipAddress.getClusterName());
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
}
