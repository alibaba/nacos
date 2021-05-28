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

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.List;

/**
 * Selector with no filtering.
 *
 * @author nkorange
 * @since 0.7.0
 */
@JsonTypeInfo(use = Id.NAME, property = "type")
public class NoneSelector extends com.alibaba.nacos.api.selector.NoneSelector implements Selector {
    
    private static final long serialVersionUID = -3752116616221930677L;
    
    static {
        JacksonUtils.registerSubtype(NoneSelector.class, SelectorType.none.name());
    }
    
    @Override
    public <T extends Instance> List<T> select(String consumer, List<T> providers) {
        return providers;
    }
}
