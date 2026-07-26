/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.spi;

import com.alibaba.nacos.plugin.control.rule.storage.ExternalRuleStorage;

/**
 * Nacos control plugin external rule storage builder SPI.
 *
 * @author xiweng.yy
 */
public interface ExternalRuleStorageBuilder {
    
    /**
     * Get plugin name.
     *
     * @return name of plugin
     */
    String getName();
    
    /**
     * Build {@link ExternalRuleStorage} implementation for current plugin if necessary.
     *
     * @return ExternalRuleStorage implementation
     */
    ExternalRuleStorage buildExternalRuleStorage();
}
