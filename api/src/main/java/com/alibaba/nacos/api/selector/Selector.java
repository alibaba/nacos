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

package com.alibaba.nacos.api.selector;

import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * The {@link Selector} mainly work on the logic of parse and select.
 * {@link #parse(Object)} allow user accept the condition which provided by Nacos to build the {@link #select(Object)} judge logic.
 * {@link #select(Object)} allow user to execute the logic to get the target result they want.
 * {@link #getType()} will return the type.
 * {@link #getContextType()} will return the context type which used by {@link #select(Object)}.
 *
 * <p>
 *     Now, Nacos only provide the {@link AbstractCmdbSelector} for user to implement their own select logic. Other type is waiting.
 * </p>
 *
 * @author chenglu
 * @date 2021-07-09 21:24
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface Selector<R, C, E> extends Serializable {
    
    /**
     * parse the selector, build the inner info which used by {@link #select(Object)}.
     *
     * @param expression expression.
     * @return selector.
     * @throws NacosException parse failed exception.
     */
    Selector<R, C, E> parse(E expression) throws NacosException;
    
    /**
     * select the target result.
     *
     * @param context selector context.
     * @return select result.
     */
    R select(C context);
    
    /**
     * Get the selector type.
     *
     * @return selector type.
     */
    String getType();
    
    /**
     * Get the select context which used by {@link #select(Object)}.
     *
     * @return selector context type.
     */
    String getContextType();
}
