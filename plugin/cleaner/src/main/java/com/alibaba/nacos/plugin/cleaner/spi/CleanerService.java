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

package com.alibaba.nacos.plugin.cleaner.spi;

import com.alibaba.nacos.plugin.cleaner.api.ExcuteSwitch;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * cleaner service.
 *
 * @author vivid
 */
public interface CleanerService {
    
    /**
     * this cleaner name.
     *
     * @return this cleaner name.
     */
    String name();
    
    /**
     * start a task for history config cleaner.
     *
     * @param excuteSwitch can this task excute
     * @param jdbcTemplate jdbcTemplate
     */
    void startConfigHistoryTask(ExcuteSwitch excuteSwitch, JdbcTemplate jdbcTemplate);
    
}
