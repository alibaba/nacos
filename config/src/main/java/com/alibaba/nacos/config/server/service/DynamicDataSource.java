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
import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.springframework.stereotype.Component;


/**
 * datasource adapter
 *
 * @author Nacos
 */
public class DynamicDataSource {

    private volatile DataSourceService localDataSourceService = null;
    private volatile DataSourceService basicDataSourceService = null;

    private static final DynamicDataSource INSTANCE = new DynamicDataSource();

    public static DynamicDataSource getInstance() {
        return INSTANCE;
    }

    public synchronized DataSourceService getDataSource() {
        try {
            if (useMemoryDB()) {
                if (localDataSourceService == null) {
                    localDataSourceService = new LocalDataSourceServiceImpl();
                    localDataSourceService.init();
                }
                return localDataSourceService;
            }
            else {
                if (basicDataSourceService == null) {
                    basicDataSourceService = new BasicDataSourceServiceImpl();
                    basicDataSourceService.init();
                }
                return basicDataSourceService;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 判断顺序：
     * 1、单机模式：mysql
     * 2、单机模式：derby
     * 3、集群模式：mysql
     * 4、集群模式：derby-cluster
     *
     * @return Whether to use derby storage
     */
    private boolean useMemoryDB() {
        return (ApplicationUtils.getStandaloneMode() && !PropertyUtil.isUseMysql())
                || PropertyUtil.isEmbeddedDistributedStorage();
    }

}
