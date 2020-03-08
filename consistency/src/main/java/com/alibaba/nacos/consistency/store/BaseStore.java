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

import com.alibaba.nacos.common.Serializer;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
abstract class BaseStore implements Store {

    protected final Serializer serializer;
    protected final String name;
    private final int openParller = 50;
    protected CommandAnalyzer commandAnalyzer;

    public BaseStore(String name, Serializer serializer) {
        this.name = name;
        this.serializer = serializer;
    }

    @Override
    public final String storeName() {
        return name;
    }

    protected synchronized final void initCommandAnalyze(CommandAnalyzer analyzer) {
        if (Objects.isNull(commandAnalyzer)) {
            commandAnalyzer = analyzer;
        }
    }

    /**
     * Abstract interface for data manipulation (except queries)
     *
     * @param data
     * @param command
     * @return boolean
     * @throws Exception
     */
    public boolean operate(String key, Object data, String command) {
        checkAnalyzer();
        return commandAnalyzer.analyze(command).apply(key, data);
    }

    private void checkAnalyzer() {
        if (Objects.isNull(commandAnalyzer)) {
            throw new NullPointerException("CommandAnalyzer is null, please set CommandAnalyzer");
        }
    }

}
