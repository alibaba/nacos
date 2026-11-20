/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oracle;

/**
 * Constants of the Oracle auth plugin.
 *
 * @author liam
 */
public final class OracleAuthPluginConstants {
    
    /**
     * Datasource dialect type that activates this plugin, mirrored from the Oracle datasource
     * dialect plugin.
     */
    public static final String ORACLE_DATA_SOURCE_TYPE = "oracle";
    
    private OracleAuthPluginConstants() {
    }
}
