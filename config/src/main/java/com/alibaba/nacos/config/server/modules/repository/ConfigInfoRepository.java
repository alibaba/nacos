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

package com.alibaba.nacos.config.server.modules.repository;

import com.alibaba.nacos.config.server.modules.entity.ConfigInfoEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * ConfigInfoRepository.
 *
 * @author Nacos
 */
public interface ConfigInfoRepository
        extends PagingAndSortingRepository<ConfigInfoEntity, Long>, JpaSpecificationExecutor<ConfigInfoEntity>, QuerydslPredicateExecutor<ConfigInfoEntity> {
    
    /**
     * findByDataIdAndGroupIdAndTenantId.
     *
     * @param dataId
     * @param groupId
     * @param tenantId
     * @return
     */
    List<ConfigInfoEntity> findByDataIdAndGroupIdAndTenantId(String dataId, String groupId, String tenantId);
    
    /**
     * findConfigMaxId.
     *
     * @return
     */
    @Query(value = "SELECT max(id) FROM config_info", nativeQuery = true)
    Long findConfigMaxId();
    
}
