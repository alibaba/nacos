/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration.persistence;

import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnInnerDatasource;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalPermissionPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalRolePersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalUserPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Nacos auth plugin embedded storage configuration.
 *
 * @author xiweng.yy
 */
@Conditional(value = {ConditionOnExternalStorage.class, ConditionOnInnerDatasource.class})
public class NacosAuthPluginExternalStorageConfig {
    
    @Bean
    public PermissionPersistService permissionPersistService() {
        return new ExternalPermissionPersistServiceImpl();
    }
    
    @Bean
    public RolePersistService rolePersistService() {
        return new ExternalRolePersistServiceImpl();
    }
    
    @Bean
    public UserPersistService userPersistService() {
        return new ExternalUserPersistServiceImpl();
    }
}
