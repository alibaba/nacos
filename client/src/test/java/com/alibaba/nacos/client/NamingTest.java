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
package com.alibaba.nacos.client;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author nkorange
 */
public class NamingTest {

    @Test
    @Ignore
    public void testServiceList() throws Exception {

        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        properties.put(PropertyKeyConst.USERNAME, "nacos");
        properties.put(PropertyKeyConst.PASSWORD, "nacos");

        NamingService namingService = NacosFactory.createNamingService(properties);

        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(800);
        instance.setWeight(2);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external");
        map.put("version", "2.0");
        instance.setMetadata(map);

        namingService.registerInstance("nacos.test.1", instance);

//        ExpressionSelector expressionSelector = new ExpressionSelector();
//        expressionSelector.setExpression("INSTANCE.metadata.registerSource = 'dubbo'");
//        ListView<String> serviceList = namingService.getServicesOfServer(1, 10, expressionSelector);

    }


}
