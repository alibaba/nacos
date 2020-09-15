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

package com.alibaba.nacos.config.server.service.capacity;

import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QGroupCapacityEntity;
import com.alibaba.nacos.config.server.modules.entity.QTenantCapacityEntity;
import com.alibaba.nacos.config.server.modules.entity.TenantCapacityEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.TenantCapacityEntityMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.TenantCapacityMapStruct;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.TenantCapacityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Tenant Capacity Service.
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class TenantCapacityPersistService {
    
    @Autowired
    private TenantCapacityRepository tenantCapacityRepository;
    
    @Autowired
    private ConfigInfoRepository configInfoRepository;
    
    public TenantCapacity getTenantCapacity(String tenantId) {
        TenantCapacityEntity tenantCapacityEntity = tenantCapacityRepository
                .findOne(QTenantCapacityEntity.tenantCapacityEntity.tenantId.eq(tenantId)).orElse(null);
        return TenantCapacityMapStruct.INSTANCE.convertTenantCapacity(tenantCapacityEntity);
    }
    
    /**
     * Insert TenantCapacity.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean insertTenantCapacity(final TenantCapacity tenantCapacity) {
        Long configInfoSize = configInfoRepository
                .count(QConfigInfoEntity.configInfoEntity.tenantId.eq(tenantCapacity.getTenant()));
        tenantCapacity.setMaxSize(configInfoSize.intValue());
        TenantCapacityEntity tenantCapacityEntity = TenantCapacityEntityMapStruct.INSTANCE
                .convertTenantCapacityEntity(tenantCapacity);
        tenantCapacityRepository.save(tenantCapacityEntity);
        return true;
    
    }
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity) {
        QTenantCapacityEntity qTenantCapacity = QTenantCapacityEntity.tenantCapacityEntity;
        TenantCapacityEntity result = tenantCapacityRepository.findOne(
                qTenantCapacity.tenantId.eq(tenantCapacity.getTenant())
                        .and(qTenantCapacity.usage.lt(tenantCapacity.getQuota())).and(qTenantCapacity.quota.eq(0)))
                .orElse(null);
        if (result == null) {
            return false;
        }
        result.setUsage(result.getUsage() + 1);
        tenantCapacityRepository.save(result);
        return true;
    }
    
    /**
     * Increment UsageWithQuotaLimit.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithQuotaLimit(TenantCapacity tenantCapacity) {
        QTenantCapacityEntity qTenantCapacity = QTenantCapacityEntity.tenantCapacityEntity;
        TenantCapacityEntity result = tenantCapacityRepository.findOne(
                qTenantCapacity.tenantId.eq(tenantCapacity.getTenant())
                        .and(qTenantCapacity.usage.lt(tenantCapacity.getQuota())).and(qTenantCapacity.quota.ne(0)))
                .orElse(null);
        if (result == null) {
            return false;
        }
        result.setGmtModified(tenantCapacity.getGmtModified());
        result.setUsage(result.getUsage() + 1);
        tenantCapacityRepository.save(result);
        return true;
    }
    
    /**
     * Increment Usage.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsage(TenantCapacity tenantCapacity) {
        TenantCapacityEntity result = tenantCapacityRepository
                .findOne(QTenantCapacityEntity.tenantCapacityEntity.tenantId.eq(tenantCapacity.getTenant()))
                .orElse(null);
        if (result == null) {
            return false;
        }
        result.setUsage(result.getUsage() + 1);
        result.setGmtModified(tenantCapacity.getGmtModified());
        tenantCapacityRepository.save(result);
        return true;
    }
    
    /**
     * DecrementUsage.
     *
     * @param tenantCapacity tenantCapacity object instance.
     * @return operate result.
     */
    public boolean decrementUsage(TenantCapacity tenantCapacity) {
        QTenantCapacityEntity qTenantCapacity = QTenantCapacityEntity.tenantCapacityEntity;
        TenantCapacityEntity result = tenantCapacityRepository
                .findOne(qTenantCapacity.tenantId.eq(tenantCapacity.getTenant()).and(qTenantCapacity.usage.gt(0)))
                .orElse(null);
        if (result == null) {
            return false;
        }
        result.setGmtModified(tenantCapacity.getGmtModified());
        if (result.getUsage() != null && result.getUsage() > 0) {
            result.setUsage(result.getUsage() - 1);
        }
        tenantCapacityRepository.save(result);
        return true;
    }
    
    /**
     * Update TenantCapacity.
     *
     * @param tenant       tenant string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return operate result.
     */
    public boolean updateTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize) {
        tenantCapacityRepository.findOne(QTenantCapacityEntity.tenantCapacityEntity.tenantId.eq(tenant))
                .ifPresent(tenantCapacity -> {
                    Optional.ofNullable(quota).ifPresent(s -> tenantCapacity.setQuota(s));
                    Optional.ofNullable(maxSize).ifPresent(s -> tenantCapacity.setMaxSize(s));
                    Optional.ofNullable(maxAggrCount).ifPresent(s -> tenantCapacity.setMaxAggrCount(s));
                    Optional.ofNullable(maxAggrSize).ifPresent(s -> tenantCapacity.setMaxAggrSize(s));
                    tenantCapacityRepository.save(tenantCapacity);
                });
        return true;
    }
    
    public boolean updateQuota(String tenant, Integer quota) {
        return updateTenantCapacity(tenant, quota, null, null, null);
    }
    
    /**
     * Correct Usage.
     *
     * @param tenant      tenant.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        Long size = configInfoRepository.count(QConfigInfoEntity.configInfoEntity.tenantId.eq(tenant));
        TenantCapacityEntity tenantCapacity = tenantCapacityRepository
                .findOne(QTenantCapacityEntity.tenantCapacityEntity.tenantId.eq(tenant)).orElse(null);
        if (tenantCapacity == null) {
            return false;
        }
        tenantCapacity.setUsage(size.intValue());
        tenantCapacity.setGmtModified(gmtModified);
        tenantCapacityRepository.save(tenantCapacity);
        return true;
    }
    
    /**
     * Get TenantCapacity List, only including id and tenantId value.
     *
     * @param lastId   lastId long value.
     * @param pageSize pageSize int value.
     * @return TenantCapacity List.
     */
    public List<TenantCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        QGroupCapacityEntity qGroupCapacity = QGroupCapacityEntity.groupCapacityEntity;
        Page<TenantCapacityEntity> page = tenantCapacityRepository.findAll(qGroupCapacity.id.gt(lastId),
                PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        return null;
    }
    
    /**
     * Delete TenantCapacity.
     *
     * @param tenant tenant string value.
     * @return operate result.
     */
    public boolean deleteTenantCapacity(final String tenant) {
        return false;
    }
}
