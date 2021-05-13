/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * A selector implement the {@link Selector}. It mainly providers a way to combine others {@link Selector},
 * like {@link NoneSelector}, ${@link LabelSelector} and etc.
 * return the instances by the order of selectors.
 * example:
 * LabelSelector1 ==> LabelSelector2 ==> NoneSelector
 * if LabelSelector1 match no instances, then will execute LabelSelector2.
 * if LabelSelector2 match some instances, then return the instance.
 * otherwise, execute the NoneSelector.
 *
 * @author chenglu
 * @date 2021-05-13 14:03
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public class MultiSelector extends AbstractSelector implements Selector {
    
    /**
     * the combine of selectors.
     */
    private List<Selector> selectors;
    
    public MultiSelector() {
        super(SelectorType.multi.name());
    }
    
    static {
        JacksonUtils.registerSubtype(MultiSelector.class, SelectorType.multi.name());
    }
    
    public List<Selector> getSelectors() {
        return selectors;
    }
    
    public void setSelectors(List<Selector> selectors) {
        this.selectors = selectors;
    }
    
    @Override
    public String getType() {
        return SelectorType.multi.name();
    }
    
    @Override
    public List<Instance> select(String consumer, List<Instance> providers) {
        for (Selector selector : selectors) {
            List<Instance> instances = selector.select(consumer, providers);
            if (instances != null && instances.size() > 0) {
                return instances;
            }
        }
        return providers;
    }
}
