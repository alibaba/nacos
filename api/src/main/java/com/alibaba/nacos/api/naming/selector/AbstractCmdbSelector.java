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

package com.alibaba.nacos.api.naming.selector;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.context.CmdbContext;

import java.util.List;

/**
 * {@link AbstractCmdbSelector} will provide one default implement of {@link Selector}, users can implement it to use the {@link CmdbContext}.
 * And return the result as default subclass of {@link Instance}.
 *
 * @author chenglu
 * @date 2021-07-09 21:29
 */
public abstract class AbstractCmdbSelector<T extends Instance> implements Selector<List<T>, CmdbContext<T>, String> {
    
    private static final String DEFAULT_CONTEXT_TYPE = "CMDB";
    
    @Override
    public List<T> select(CmdbContext<T> context) {
        return doSelect(context);
    }
    
    /**
     * The real select implement by subclass.
     *
     * @param context selector context {@link CmdbContext}.
     * @return the select result.
     */
    protected abstract List<T> doSelect(CmdbContext<T> context);
    
    @Override
    public String getContextType() {
        return DEFAULT_CONTEXT_TYPE;
    }
}
