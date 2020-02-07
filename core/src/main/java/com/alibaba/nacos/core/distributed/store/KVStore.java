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

package com.alibaba.nacos.core.distributed.store;

import java.util.Collection;
import java.util.Map;

/**
 * Key-value pair data storage structure abstraction
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public abstract class KVStore extends BaseStore {

    public KVStore(String name) {
        super(name);
    }

    /**
     * get batch data by key list
     *
     * @param keys {@link Collection <String>}
     * @return {@link Map <String, ? extends Record>}
     */
    public abstract Map<String, ? extends Record> batchGet(Collection<String> keys);

    /**
     * get data by key
     *
     * @param key data key
     * @return target data {@link <T extends Record>}
     */
    public abstract <T extends Record> T getByKey(String key);

    /**
     * all data keys
     *
     * @return {@link Collection <String>}
     */
    public abstract Collection<String> allKeys();

}
