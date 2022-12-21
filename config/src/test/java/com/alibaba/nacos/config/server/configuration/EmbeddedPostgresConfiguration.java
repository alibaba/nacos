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

package com.alibaba.nacos.config.server.configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

import com.alibaba.nacos.config.server.service.datasource.ExternalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalCommonPersistServiceImpl;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration
public class EmbeddedPostgresConfiguration {

    public void init(ConfigurableEnvironment environment) {
        System.setProperty("nacos.standalone", "false");
        PropertyUtil.setUseExternalDB(true);
        EnvUtil.setEnvironment(environment);
    }

    @Bean
    public EmbeddedPostgres embeddedPostgresCustomizer(ConfigurableEnvironment environment) throws IOException {
        init(environment);
        return EmbeddedPostgres.builder().setPGStartupWait(Duration.ofSeconds(60L)).setPort(5432).start();
    }
}
