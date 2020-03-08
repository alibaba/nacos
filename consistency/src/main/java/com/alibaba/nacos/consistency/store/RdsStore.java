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

import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import java.util.Collection;

/**
 * Relational data storage structure abstraction
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public abstract class RdsStore extends BaseStore {

    public RdsStore(String name) {
        super(name, SerializeFactory.getDefault());
    }

    public RdsStore(String name, Serializer serializer) {
        super(name, serializer);
    }

    /**
     * query by command
     *
     * @param command this operate command name
     * @param <T>     type
     * @return target data
     */
    public abstract <T> T query(String command);

    /**
     * query by command
     *
     * @param command this operate command name
     * @param <T>     type
     * @return target datas
     */
    public abstract <T> Collection<T> queryBatch(String command);

}
