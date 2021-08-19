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

package com.alibaba.nacos.api.selector.context;

import com.alibaba.nacos.api.selector.Selector;

/**
 * The {@link SelectorContextBuilder} mainly for provide the context for {@link Selector#select(Object)}.
 * It provides {@link #build(Object, Object)} method for build context. And also provide {@link #getContextType()} for get the contextType.
 *
 * @author chenglu
 * @date 2021-07-09 21:34
 */
public interface SelectorContextBuilder<T, C, P> {
    
    /**
     * build the context for {@link Selector#select(Object)}. The user must provide consumer and provider.
     * we provide {@link CmdbContext} for user default who want to use the {@link com.alibaba.nacos.api.naming.pojo.Instance}'s CMDB info.
     *
     * @param consumer consumer who launch the select.
     * @param provider the provides who are selected by consumer.
     * @return selectorContext use by {@link Selector#select(Object)}.
     */
    T build(C consumer, P provider);
    
    /**
     * the contextType. we provide the CMDB context type default.
     *
     * @return the context type.
     */
    String getContextType();
}
