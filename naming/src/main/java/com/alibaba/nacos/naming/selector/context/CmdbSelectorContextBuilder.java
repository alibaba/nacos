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

package com.alibaba.nacos.naming.selector.context;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.cmdb.pojo.PreservedEntityTypes;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.context.CmdbContext;
import com.alibaba.nacos.api.selector.context.SelectorContextBuilder;
import com.alibaba.nacos.cmdb.service.CmdbReader;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@link CmdbSelectorContextBuilder} will build default {@link CmdbContext}, it query the consumer and providers' CMDB {@link Entity}.
 *
 * @author chenglu
 * @date 2021-07-16 11:58
 */
public class CmdbSelectorContextBuilder<T extends Instance> implements SelectorContextBuilder<CmdbContext<Instance>, String, List<T>> {
    
    private static final String CONTEXT_TYPE = "CMDB";
    
    /**
     * Get the {@link CmdbReader} from Spring container.
     *
     * @return {@link CmdbReader}.
     */
    public CmdbReader getCmdbReader() {
        return ApplicationUtils.getBean(CmdbReader.class);
    }
    
    @Override
    public CmdbContext<Instance> build(String consumer, List<T> provider) {
        // build consumer context
        Entity consumerEntity = getCmdbReader().queryEntity(consumer, PreservedEntityTypes.ip.name());
        Instance consumerInstance = new Instance();
        consumerInstance.setIp(consumer);
        CmdbContext.CmdbInstance<Instance> consumerCmdbInstance = new CmdbContext.CmdbInstance<>();
        consumerCmdbInstance.setEntity(consumerEntity);
        consumerCmdbInstance.setInstance(consumerInstance);
        CmdbContext<Instance> cmdbContext = new CmdbContext<>();
        cmdbContext.setConsumer(consumerCmdbInstance);

        // build providers context
        List<CmdbContext.CmdbInstance<Instance>> providerCmdbInstances = Optional.ofNullable(provider)
                .orElse(Collections.emptyList())
                .stream()
                .map(is -> {
                    CmdbContext.CmdbInstance<Instance> providerCmdbInstance = new CmdbContext.CmdbInstance<>();
                    providerCmdbInstance.setInstance(is);
                    Entity providerEntity = getCmdbReader().queryEntity(is.getIp(), PreservedEntityTypes.ip.name());
                    providerCmdbInstance.setEntity(providerEntity);
                    return providerCmdbInstance;
                })
                .collect(Collectors.toList());
        cmdbContext.setProviders(providerCmdbInstances);
        
        return cmdbContext;
    }
    
    @Override
    public String getContextType() {
        return CONTEXT_TYPE;
    }
}
