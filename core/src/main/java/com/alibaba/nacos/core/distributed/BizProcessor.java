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

import com.alibaba.nacos.common.model.ResResult;

import java.util.Collection;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface BizProcessor {

    /**
     * register log consumer
     *
     * @param consumer {@link LogConsumer}
     */
    void registerLogConsumer(LogConsumer consumer);

    /**
     * deregister log consumer
     *
     * @param operation {@link LogConsumer}
     */
    void deregisterLogConsumer(String operation);

    /**
     * get data by key
     *
     * @param key data
     * @return target type data
     */
    <T> T getData(String key);

    /**
     * Process Submitted Log
     *
     * @param log {@link Log}
     * @return {@link ResResult<Boolean>}
     */
    ResResult<Boolean> onApply(Log log);

    /**
     * get all log consumer
     *
     * @return {@link LogConsumer}
     */
    Collection<LogConsumer> allLogConsumer();

    /**
     * this BizProcessor which interest biz
     *
     * @return biz name
     */
    String bizInfo();

    /**
     * Determine whether the key is within the business you are following
     *
     * @param key datum-key
     * @return interest result
     */
    boolean interest(String key);

}
