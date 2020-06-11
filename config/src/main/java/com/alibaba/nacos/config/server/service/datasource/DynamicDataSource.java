/*
 *
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
 *
 */
package com.alibaba.nacos.config.server.service.datasource;

import com.alibaba.nacos.config.server.utils.PropertyUtil;

/**
 * datasource adapter
 *
 * @author Nacos
 */
public class DynamicDataSource {

    private DataSourceService localDataSourceService = null;
    private DataSourceService basicDataSourceService = null;

    private static final DynamicDataSource INSTANCE = new DynamicDataSource();

    public static DynamicDataSource getInstance() {
        return INSTANCE;
    }

    public synchronized DataSourceService getDataSource() {
        try {

            // Embedded storage is used by default in stand-alone mode
            // In cluster mode, external databases are used by default

            if (PropertyUtil.isEmbeddedStorage()) {
                if (localDataSourceService == null) {
                    localDataSourceService = new LocalDataSourceServiceImpl();
                    localDataSourceService.init();
                }
                return localDataSourceService;
            }
            else {
                if (basicDataSourceService == null) {
                    basicDataSourceService = new ExternalDataSourceServiceImpl();
                    basicDataSourceService.init();
                }
                return basicDataSourceService;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
