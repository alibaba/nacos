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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.test.base.BaseClusterTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Disabled
@TestMethodOrder(MethodName.class)
class NamingRaft_DITCase extends BaseClusterTest {
    
    @Test
    void test_register_instance() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        Instance instance = new Instance();
        instance.setEphemeral(true);  //是否临时实例
        instance.setServiceName(serviceName);
        instance.setClusterName("c1");
        instance.setIp("11.11.11.11");
        instance.setPort(80);
        
        try {
            inaming7.registerInstance(serviceName, instance);
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
            return;
        }
        List<Instance> list = inaming8.getAllInstances(serviceName);
        assertEquals(1, list.size());
        
        Instance host = list.get(0);
        
        assertEquals(host.getIp(), instance.getIp());
        assertEquals(host.getPort(), instance.getPort());
        assertEquals(host.getServiceName(), NamingUtils.getGroupedName(instance.getServiceName(), "DEFAULT_GROUP"));
        assertEquals(host.getClusterName(), instance.getClusterName());
    }
    
}
