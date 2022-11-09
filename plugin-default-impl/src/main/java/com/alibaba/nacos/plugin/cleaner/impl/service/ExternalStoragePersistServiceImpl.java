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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * External Storage Persist Service.
 *
 * @author vivid
 */
public class ExternalStoragePersistServiceImpl implements PersistService {

    JdbcTemplate jdbcTemplate;

    CleanerConfig cleanerConfig;

    public ExternalStoragePersistServiceImpl(JdbcTemplate jdbcTemplate, CleanerConfig cleanerConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanerConfig = cleanerConfig;
    }

    @Override
    public void removeConfigHistory(Timestamp startTime, int limitSize) {
        String sql = "DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?";
        sql = updateSql(sql);
        jdbcTemplate.update(sql, startTime, limitSize);

    }

    @Override
    public int findConfigHistoryCount() {
        String sql = "SELECT count(*) FROM his_config_info";
        sql = updateSql(sql);
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String updateSql(String sql) {
        if (isDerby()) {
            return sql.replaceAll("LIMIT \\?", "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
        }
        return sql;
    }

    private boolean isDerby() {
        return EnvUtil.getStandaloneMode() && !"mysql".equals(cleanerConfig.getDataSource());
    }
}
