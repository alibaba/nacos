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

/**
 * The types of selector accepted by Nacos.
 *
 * @author nkorange
 * @since 0.7.0
 */
public enum SelectorType {
    /**
     * not match any type.
     */
    unknown,
    /**
     * not filter out any entity.
     */
    none,
    /**
     * select by label.
     */
    label,
    /**
     * select by cluster.
     */
    cluster,
    /**
     * select by health state.
     */
    health,
    /**
     * select by enable state.
     */
    enable
}
