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

package com.alibaba.nacos.plugin.cleaner.impl.service;

import com.alibaba.nacos.plugin.cleaner.config.CleanerConfig;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * embedded storage persist service.
 *
 * @author vivid
 */
public class EmbeddedStoragePersistServiceImpl implements PersistService {

    JdbcTemplate jdbcTemplate;

    CleanerConfig cleanerConfig;

    public EmbeddedStoragePersistServiceImpl(JdbcTemplate jdbcTemplate, CleanerConfig cleanerConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanerConfig = cleanerConfig;
    }

    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        String sql = "DELETE FROM his_config_info WHERE id IN( "
                + "SELECT id FROM his_config_info WHERE gmt_modified < ? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY)";
        jdbcTemplate.update(sql, startTime, limitSize);

    }

    @Override
    public int findConfigHistoryCount() {
        String sql = "SELECT count(*) FROM his_config_info ";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

}

