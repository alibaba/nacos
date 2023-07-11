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

package com.alibaba.nacos.plugin.config.constants;

/**
 * Config change type depend on the pointcut method.
 *
 * @author liyunfei
 */
public enum ConfigChangePointCutTypes {
    
    /**
     * Publish or update config through http.
     */
    PUBLISH_BY_HTTP("publishOrUpdateByHttp"),
    /**
     * Publish config through rpc.
     */
    PUBLISH_BY_RPC("publishOrUpdateByRpc"),
    /**
     * Remove by id through http.
     */
    REMOVE_BY_HTTP("removeSingleByHttp"),
    /**
     * Remove through rpc.
     */
    REMOVE_BY_RPC("removeSingleByRpc"),
    /**
     * Import config file through http/console.
     */
    IMPORT_BY_HTTP("importFileByHttp"),
    /**
     * Remove by ids through http.
     */
    REMOVE_BATCH_HTTP("removeBatchByHttp");
    
    private final String value;
    
    ConfigChangePointCutTypes(String value) {
        this.value = value;
    }
    
    public String value() {
        return value;
    }
    
    public boolean equals(ConfigChangePointCutTypes configChangePointCutTypes) {
        return this.compareTo(configChangePointCutTypes) == 0;
    }
}
