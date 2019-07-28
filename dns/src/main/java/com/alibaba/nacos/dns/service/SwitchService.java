/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.service;

import java.util.Map;

/**
 * @author paderlol
 * @date 2019年07月28日, 16:23:30
 */
public interface SwitchService {

    /**
     * Get dns system config
     * @param domain the domain
     * @return the system config
     * @author zhanglong
     * @date 2019年07月28日, 16:23:30
     */
    Map<String, String> getSystemConfig(String domain);

    /**
     * Update system config.
     *
     * @param entry the entry
     * @param value the value
     * @author zhanglong
     * @date 2019年07月28日, 16:23:30
     */
    void updateSystemConfig(String entry, String value);
}
