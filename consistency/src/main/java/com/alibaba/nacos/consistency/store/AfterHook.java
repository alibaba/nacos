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

package com.alibaba.nacos.consistency.store;

/**
 * Hooks after data manipulation
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@FunctionalInterface
public interface AfterHook<T> {

    /**
     * Hooks after data manipulation
     *
     * @param key   key
     * @param data  data Transparently transmitting the data inserted by the user
     * @param item  Actually stored data {@link com.alibaba.nacos.consistency.store.KVStore.Item}
     * @param isPut is put operation
     */
    void hook(String key, T data, KVStore.Item item, boolean isPut);

}
