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
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Store<T extends Record> {

    /**
     * register command dispatcher
     *
     * @param dispatcher {@link CommandDispatcher}
     */
    void initCommandAnalyze(CommandDispatcher dispatcher);

    /**
     * data operate
     *
     * @param data which extend {@link Record}
     * @param command data operate
     * @return this operate is success
     * @throws Exception
     */
    boolean operate(T data, String command) throws Exception;

    /**
     * data batch operate
     *
     * @param datas operate data, like <Operate, Collection<Record>>
     * @return this operate is success
     * @throws Exception
     */
    boolean batchOperate(Map<String, Collection<? extends Record>> datas) throws Exception;

    /**
     * The storage belongs to that business
     *
     * @return business name
     */
    String biz();

}
