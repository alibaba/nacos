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

package com.alibaba.nacos.core.distributed;

import java.util.Collection;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Store<T extends Record> {

    /**
     * Data batch insert
     *
     * @param datas {@link Collection<? extends Record>}
     */
    void batchSave(Collection<? extends Record> datas);

    /**
     * Data batch update
     *
     * @param datas {@link Collection<? extends Record>}
     */
    void batchUpdate(Collection<? extends Record> datas);

    /**
     * Data batch remove
     *
     * @param key {@link Collection<String>}
     */
    void batchRemove(Collection<String> key);

    /**
     * get batch data by key list
     *
     * @param keys {@link Collection <String>}
     * @return {@link Map <String, ? extends Record>}
     */
    Map<String, ? extends Record> batchGet(Collection<String> keys);

    /**
     * get data by key
     *
     * @param key data key
     * @return target data {@link <T extends Record>}
     */
    <T extends Record> T getByKey(String key);

    /**
     * all data keys
     *
     * @return {@link Collection <String>}
     */
    Collection<String> allKeys();

    /**
     * The storage belongs to that business
     *
     * @return business name
     */
    String biz();

}
