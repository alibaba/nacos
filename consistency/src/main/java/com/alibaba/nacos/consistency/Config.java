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

package com.alibaba.nacos.consistency;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Consistent protocol related configuration objects
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Config<L extends LogProcessor> extends Serializable {

    /**
     * Add configuration content
     *
     * @param key   config key
     * @param value config value
     */
    void setVal(String key, String value);

    /**
     * get configuration content by key
     *
     * @param key config key
     * @return config value
     */
    String getVal(String key);

    /**
     * get configuration content by key, if not found, use default-val
     *
     * @param key        config key
     * @param defaultVal default value
     * @return config value
     */
    String getValOfDefault(String key, String defaultVal);

    /**
     * get LogProcessors
     *
     * @return {@link List<LogProcessor>}
     */
    List<L> listLogProcessor();

    /**
     * add {@link LogProcessor} processor
     *
     * @param processors {@link LogProcessor} array
     */
    void addLogProcessors(Collection<L> processors);

}
