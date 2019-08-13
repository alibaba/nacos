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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;

/**
 * datasource adapter
 *
 * @author Nacos
 */
@Component
public class DynamicDataSource implements ApplicationContextAware {

    @Autowired
    private PropertyUtil propertyUtil;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public DataSourceService getDataSource() {
        DataSourceService dataSourceService = null;

        if (STANDALONE_MODE && !propertyUtil.isStandaloneUseMysql()) {
            dataSourceService = (DataSourceService)applicationContext.getBean("localDataSourceService");
        } else {
            dataSourceService = (DataSourceService)applicationContext.getBean("basicDataSourceService");
        }

        return dataSourceService;
    }

}
