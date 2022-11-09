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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Date;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExternalStoragePersistServiceImplTest {

    @InjectMocks
    ExternalStoragePersistServiceImpl externalStoragePersistService;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    CleanerConfig cleanerConfig;

    @Test
    public void testDerbyRemoveConfigHistory() {
        EnvUtil.setIsStandalone(true);
        when(cleanerConfig.getDataSource()).thenReturn("derby");
        String sql = "DELETE FROM his_config_info WHERE gmt_modified < ? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        externalStoragePersistService.removeConfigHistory(new Timestamp(0L), 0);
        Mockito.verify(jdbcTemplate).update(sql, new Timestamp(0L), 0);

    }

    @Test
    public void testMysqlRemoveConfigHistory() {
        when(cleanerConfig.getDataSource()).thenReturn("mysql");
        String sql = "DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?";
        Timestamp startTime = new Timestamp(new Date().getTime());
        externalStoragePersistService.removeConfigHistory(startTime, 0);
        Mockito.verify(jdbcTemplate).update(sql, startTime, 0);

    }

    @Test
    public void testFindConfigHistoryCountByTime() {
        String sql = "SELECT count(*) FROM his_config_info WHERE gmt_modified < ?";
        Timestamp startTime = new Timestamp(new Date().getTime());
        when(jdbcTemplate.queryForObject(sql, new Object[]{startTime}, Integer.class)).thenReturn(3);
        int count = externalStoragePersistService.findConfigHistoryCount();
        Assert.assertEquals(count, 3);
    }
}