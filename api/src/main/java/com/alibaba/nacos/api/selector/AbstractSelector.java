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

package com.alibaba.nacos.api.selector;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.io.Serializable;
import java.util.List;

/**
 * Abstract selector that only contains a type, used for api to set selector type without actual selector logic.
 *
 * @author nkorange
 * @since 0.7.0
 */
@JsonTypeInfo(use = Id.NAME, property = "type", defaultImpl = NoneSelector.class)
public abstract class AbstractSelector implements Serializable, Selector<List<Instance>, List<Instance>, String> {
    
    private static final long serialVersionUID = 4530233098102379229L;
    
    /**
     * The type of this selector, each child class should announce its own unique type.
     */
    private final String type;
    
    protected AbstractSelector(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
    
    @Override
    public Selector<List<Instance>, List<Instance>, String> parse(String expression) throws NacosException {
        return null;
    }
    
    @Override
    public List<Instance> select(List<Instance> context) {
        return context;
    }
    
    @Override
    public String getContextType() {
        return SelectorType.none.name();
    }
}
