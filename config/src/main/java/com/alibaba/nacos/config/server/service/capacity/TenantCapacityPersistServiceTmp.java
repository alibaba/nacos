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


import com.alibaba.nacos.config.server.modules.entity.*;
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
 * @author Nacos
 */
@Service
public class TenantCapacityPersistServiceTmp {

    @Autowired
    private TenantCapacityRepository tenantCapacityRepository;

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    public TenantCapacityEntity getTenantCapacity(String tenantId) {
        return tenantCapacityRepository.findOne(QTenantCapacity
            .tenantCapacity.tenantId.eq(tenantId)).orElse(null);
    }

    public boolean insertTenantCapacity(final TenantCapacityEntity tenantCapacity) {
        Long configInfoSize = configInfoRepository.count(QConfigInfo.configInfo.tenantId.eq(tenantCapacity.getTenantId()));
        tenantCapacity.setMaxSize(configInfoSize.intValue());
        tenantCapacityRepository.save(tenantCapacity);
        return true;
    }

    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacityEntity tenantCapacity) {
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        TenantCapacityEntity result = tenantCapacityRepository.findOne(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId())
            .and(qTenantCapacity.usage.lt(tenantCapacity.getQuota()))
            .and(qTenantCapacity.quota.eq(0)))
            .orElse(null);
        if (result == null) {
            return false;
        }
        result.setUsage(result.getUsage() + 1);
        tenantCapacityRepository.save(result);
        return true;
    }

    public boolean incrementUsageWithQuotaLimit(TenantCapacityEntity tenantCapacity) {
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        TenantCapacityEntity result = tenantCapacityRepository.findOne(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId())
            .and(qTenantCapacity.usage.lt(tenantCapacity.getQuota()))
            .and(qTenantCapacity.quota.ne(0)))
            .orElse(null);
        if (result == null) {
            return false;
        }
        result.setGmtModified(tenantCapacity.getGmtModified());
        result.setUsage(result.getUsage() + 1);
        tenantCapacityRepository.save(result);
        return true;
    }

    public boolean incrementUsage(TenantCapacityEntity tenantCapacity) {
        TenantCapacityEntity result = tenantCapacityRepository
            .findOne(QTenantCapacity.tenantCapacity.tenantId.eq(tenantCapacity.getTenantId()))
            .orElse(null);
        if (result == null) {
            return false;
        }
        result.setUsage(result.getUsage() + 1);
        result.setGmtModified(tenantCapacity.getGmtModified());
        tenantCapacityRepository.save(result);
        return true;
    }

    public boolean decrementUsage(TenantCapacityEntity tenantCapacity) {
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        TenantCapacityEntity result = tenantCapacityRepository.findOne(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId())
            .and(qTenantCapacity.usage.gt(0)))
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

    public boolean updateTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
                                        Integer maxAggrSize) {
        tenantCapacityRepository.findOne(QTenantCapacity.tenantCapacity.tenantId.eq(tenant))
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

    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        Long size = configInfoRepository.count(QConfigInfo.configInfo.tenantId.eq(tenant));
        TenantCapacityEntity tenantCapacity = tenantCapacityRepository.findOne(QTenantCapacity.tenantCapacity.tenantId.eq(tenant))
            .orElse(null);
        if (tenantCapacity == null) {
            return false;
        }
        tenantCapacity.setUsage(size.intValue());
        tenantCapacity.setGmtModified(gmtModified);
        tenantCapacityRepository.save(tenantCapacity);
        return true;
    }

    /**
     * 获取TenantCapacity列表，只有id、tenantId有值
     *
     * @param lastId   id > lastId
     * @param pageSize 页数
     * @return TenantCapacity列表
     */
    public List<TenantCapacityEntity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Page<TenantCapacityEntity> page = tenantCapacityRepository.findAll(qGroupCapacity.id.gt(lastId),
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        return page.getContent();
    }

}
