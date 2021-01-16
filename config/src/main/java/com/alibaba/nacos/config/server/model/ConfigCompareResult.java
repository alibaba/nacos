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

package com.alibaba.nacos.config.server.model;

import java.util.Collections;
import java.util.List;

/**
 * Config compare result.
 *
 * @author BlackBAKA
 * @date 2021/01/16
 */
public class ConfigCompareResult {

    private List<ConfigInfo> configInfoList = Collections.emptyList();

    private List<ConfigContentKeyValue> keyValueList = Collections.emptyList();

    private int count = 0;

    public List<ConfigInfo> getConfigInfoList() {
        return configInfoList;
    }

    public void setConfigInfoList(List<ConfigInfo> configInfoList) {
        this.configInfoList = configInfoList;
    }

    public List<ConfigContentKeyValue> getKeyValueList() {
        return keyValueList;
    }

    public void setKeyValueList(List<ConfigContentKeyValue> keyValueList) {
        this.keyValueList = keyValueList;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
