/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.log;

import java.net.URL;

/**
 * logback configurator interface,different version can adapter this.
 * @author hujun
 */
public interface NacosLogbackConfigurator {
    
    /**
     * config logback.
     * @param resourceUrl resourceUrl
     * @throws Exception exception
     */
    void configure(URL resourceUrl) throws Exception;
    
    /**
     * logback configurator will be sorted by version.
     * @return version
     */
    int getVersion();
    
    /**
     * set loggerContext.
     * @param loggerContext loggerContext
     */
    void setContext(Object loggerContext);
}
