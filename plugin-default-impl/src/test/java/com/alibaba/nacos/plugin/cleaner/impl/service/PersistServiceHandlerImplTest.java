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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;

@RunWith(MockitoJUnitRunner.class)
public class PersistServiceHandlerImplTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    CleanerConfig cleanerConfig;

    @Test
    public void externalStoragePersistServiceTest() {
        EnvUtil.setIsStandalone(false);
        PersistServiceHandlerImpl handler = new PersistServiceHandlerImpl(jdbcTemplate, cleanerConfig);
        assert handler.persistService instanceof ExternalStoragePersistServiceImpl;
    }

    @Test
    public void embeddedStoragePersistServiceImplTest() {
        EnvUtil.setIsStandalone(true);
        PersistServiceHandlerImpl handler = new PersistServiceHandlerImpl(jdbcTemplate, cleanerConfig);
        assert handler.persistService instanceof EmbeddedStoragePersistServiceImpl;
    }

}