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

import com.alibaba.nacos.persistence.configuration.condition.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnInnerDatasource;
import com.alibaba.nacos.plugin.auth.impl.persistence.EmbeddedPermissionPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.EmbeddedRolePersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.EmbeddedUserPersistServiceImpl;
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
@Conditional(value = {ConditionOnEmbeddedStorage.class, ConditionOnInnerDatasource.class})
public class NacosAuthPluginEmbeddedStorageConfig {
    
    @Bean
    public PermissionPersistService permissionPersistService(DatabaseOperate databaseOperate) {
        return new EmbeddedPermissionPersistServiceImpl(databaseOperate);
    }
    
    @Bean
    public RolePersistService rolePersistService(DatabaseOperate databaseOperate) {
        return new EmbeddedRolePersistServiceImpl(databaseOperate);
    }
    
    @Bean
    public UserPersistService userPersistService(DatabaseOperate databaseOperate) {
        return new EmbeddedUserPersistServiceImpl(databaseOperate);
    }
}
