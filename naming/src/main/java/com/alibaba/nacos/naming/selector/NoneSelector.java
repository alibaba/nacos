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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.Selector;

import java.util.List;

/**
 * Selector with no filtering.
 *
 * @author chenglu
 * @date 2021-08-04 13:28
 */
public class NoneSelector<T extends Instance> implements Selector<List<T>, List<T>, String> {
    
    private static final String CONTEXT_TYPE = "NONE";
    
    private static final String TYPE = "none";
    
    @Override
    public Selector<List<T>, List<T>, String> parse(String condition) throws NacosException {
        return this;
    }
    
    @Override
    public List<T> select(List<T> context) {
        return context;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public String getContextType() {
        return CONTEXT_TYPE;
    }
}
