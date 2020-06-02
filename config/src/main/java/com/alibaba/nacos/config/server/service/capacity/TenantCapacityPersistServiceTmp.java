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

@Service
public class TenantCapacityPersistServiceTmp {

    @Autowired
    private TenantCapacityRepository tenantCapacityRepository;

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    public TenantCapacity getTenantCapacity(String tenantId) {
        Iterable<TenantCapacity> tenantCapacities = tenantCapacityRepository.findAll(QTenantCapacity
            .tenantCapacity.tenantId.eq(tenantId));
        return tenantCapacities.iterator().next();
    }

    public boolean insertTenantCapacity(final TenantCapacity tenantCapacity) {
        Long configInfoSize = configInfoRepository.count(QConfigInfo.configInfo.tenantId.eq(tenantCapacity.getTenantId()));
        tenantCapacity.setMaxSize(configInfoSize.intValue());
        tenantCapacityRepository.save(tenantCapacity);
        return true;
    }

    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity) {
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId())
            .and(qTenantCapacity.usage.lt(tenantCapacity.getUsage()))
            .and(qTenantCapacity.quota.eq(0)));
        iterable.forEach(s -> {
            s.setGmtModified(tenantCapacity.getGmtModified());
            s.setUsage(tenantCapacity.getUsage() + 1);
        });
        tenantCapacityRepository.saveAll(iterable);
        return ((List) iterable).size() == 1;
    }

    public boolean incrementUsageWithQuotaLimit(TenantCapacity tenantCapacity) {
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId())
            .and(qTenantCapacity.usage.lt(tenantCapacity.getUsage()))
            .and(qTenantCapacity.quota.ne(0)));
        iterable.forEach(s -> {
            s.setGmtModified(tenantCapacity.getGmtModified());
            s.setUsage(tenantCapacity.getUsage() + 1);
        });
        tenantCapacityRepository.saveAll(iterable);
        return ((List) iterable).size() == 1;
    }

    public boolean incrementUsage(TenantCapacity tenantCapacity) {
        Iterable<TenantCapacity> iterable = tenantCapacityRepository
            .findAll(QTenantCapacity.tenantCapacity.tenantId.eq(tenantCapacity.getTenantId()));
        iterable.forEach(s -> {
            s.setUsage(s.getUsage() + 1);
            s.setGmtModified(tenantCapacity.getGmtModified());
        });
        tenantCapacityRepository.saveAll(iterable);
        return ((List) iterable).size() == 1;
    }

    public boolean decrementUsage(TenantCapacity tenantCapacity) {
        QTenantCapacity qTenantCapacity = QTenantCapacity.tenantCapacity;
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll(qTenantCapacity.tenantId.eq(tenantCapacity.getTenantId())
            .and(qTenantCapacity.usage.gt(0)));
        iterable.forEach(s -> {
            s.setGmtModified(tenantCapacity.getGmtModified());
            s.setUsage(s.getUsage() - 1);
        });
        tenantCapacityRepository.saveAll(iterable);
        return ((List) iterable).size() == 1;
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
        Iterable<TenantCapacity> iterable = tenantCapacityRepository.findAll(QTenantCapacity.tenantCapacity.tenantId.eq(tenant)
            .and(QTenantCapacity.tenantCapacity.gmtModified.eq(gmtModified)));
        iterable.forEach(tenantCapacity -> tenantCapacity.setUsage(size.intValue()));
        tenantCapacityRepository.saveAll(iterable);
        return true;
    }

    /**
     * 获取TenantCapacity列表，只有id、tenantId有值
     *
     * @param lastId   id > lastId
     * @param pageSize 页数
     * @return TenantCapacity列表
     */
    public List<TenantCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Page<TenantCapacity> page = tenantCapacityRepository.findAll(qGroupCapacity.id.gt(lastId),
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        return page.getContent();
    }

}
