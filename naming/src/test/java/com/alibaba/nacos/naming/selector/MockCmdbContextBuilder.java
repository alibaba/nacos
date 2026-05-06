/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.context.CmdbContext;
import com.alibaba.nacos.api.selector.context.SelectorContextBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link SelectorContextBuilder} mock implement.
 *
 * @author chenglu
 * @date 2021-07-14 19:22
 */
public class MockCmdbContextBuilder implements SelectorContextBuilder<CmdbContext, String, List<Instance>> {
    
    @Override
    public CmdbContext build(String consumerIp, List<Instance> provider) {
        CmdbContext.CmdbInstance<Instance> con = new CmdbContext.CmdbInstance<>();
        Instance instance = new Instance();
        instance.setIp(consumerIp);
        con.setInstance(instance);
        CmdbContext cmdbContext = new CmdbContext();
        cmdbContext.setConsumer(con);
        
        List<CmdbContext.CmdbInstance> providers = provider.stream().map(p -> {
            Entity entity = new Entity();
            entity.setType("mockType");
            entity.setName("mockName");
            Map<String, String> labels = new HashMap<>();
            labels.put("key", "value");
            entity.setLabels(labels);
            
            CmdbContext.CmdbInstance<Instance> pro = new CmdbContext.CmdbInstance<>();
            pro.setInstance(p);
            pro.setEntity(entity);
            return pro;
        }).collect(Collectors.toList());
        cmdbContext.setProviders(providers);
        return cmdbContext;
    }
    
    @Override
    public String getContextType() {
        return "MOCK_CMDB";
    }
}
