package com.alibaba.nacos.config.server.service.capacity;

import com.alibaba.nacos.config.server.modules.entity.*;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.GroupCapacityRepository;
import com.alibaba.nacos.config.server.modules.repository.TenantCapacityRepository;
import com.querydsl.core.BooleanBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static com.alibaba.nacos.config.server.service.capacity.GroupCapacityPersistService.CLUSTER;


@Service
public class GroupCapacityPersistServiceTmp {

    @Autowired
    private GroupCapacityRepository groupCapacityRepository;

    @Autowired
    private TenantCapacityRepository tenantCapacityRepository;

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    public GroupCapacity getGroupCapacity(String groupId) {
        return groupCapacityRepository.findAll(QGroupCapacity.groupCapacity.groupId.eq(groupId))
            .iterator()
            .next();
    }


    public Capacity getClusterCapacity() {
        return getGroupCapacity(CLUSTER);
    }


    public boolean insertGroupCapacity(final GroupCapacity capacity) {
        Long configInfoSize;
        if (CLUSTER.equals(capacity.getGroupId())) {
            configInfoSize = configInfoRepository.count();
        } else {
            BooleanBuilder booleanBuilder = new BooleanBuilder();
            QConfigInfo qConfigInfo = QConfigInfo.configInfo;
            booleanBuilder.and(qConfigInfo.groupId.eq(capacity.getGroupId()));
            booleanBuilder.and(qConfigInfo.tenantId.isEmpty());
            configInfoSize = configInfoRepository.count(booleanBuilder);
        }
        return insertGroupCapacity(configInfoSize, capacity);
    }


    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
            .and(qGroupCapacity.usage.lt(groupCapacity.getUsage()))
            .and(qGroupCapacity.quota.eq(0)));
        iterable.forEach(s -> {
            s.setGmtModified(groupCapacity.getGmtModified());
            s.setUsage(groupCapacity.getUsage() + 1);
        });
        groupCapacityRepository.saveAll(iterable);
        return true;
    }

//    public TenantCapacity getTenantCapacity(String tenantId) {
//        return tenantCapacityRepository.findAll(QTenantCapacity.tenantCapacity.tenantId.eq(tenantId))
//            .iterator()
//            .next();
//    }



    private boolean insertGroupCapacity(final Long configInfoSize, final GroupCapacity capacity) {
        capacity.setUsage(configInfoSize.intValue());
        groupCapacityRepository.save(capacity);
        return true;
    }

    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
            .and(qGroupCapacity.usage.lt(groupCapacity.getUsage()))
            .and(qGroupCapacity.quota.ne(0)));
        List<GroupCapacity> list = (List<GroupCapacity>) iterable;
        if (list.size() > 1) {
            return false;
        }
        iterable.forEach(s -> {
            s.setGmtModified(groupCapacity.getGmtModified());
            s.setUsage(groupCapacity.getUsage() + 1);
        });
        groupCapacityRepository.saveAll(iterable);
        return true;
    }

    public boolean incrementUsage(GroupCapacity groupCapacity) {
        Iterable<GroupCapacity> iterable = groupCapacityRepository
            .findAll(QGroupCapacity.groupCapacity.groupId.eq(groupCapacity.getGroupId()));
        iterable.forEach(s -> {
            s.setUsage(s.getUsage() + 1);
            s.setGmtModified(groupCapacity.getGmtModified());
        });
        groupCapacityRepository.saveAll(iterable);
        return true;
    }

    public boolean decrementUsage(GroupCapacity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
            .and(qGroupCapacity.usage.gt(0)));
        List<GroupCapacity> list = (List<GroupCapacity>) iterable;
        if (list.size() > 1) {
            return false;
        }
        list.forEach(s -> {
            s.setGmtModified(groupCapacity.getGmtModified());
            s.setUsage(s.getUsage() - 1);
        });
        groupCapacityRepository.saveAll(list);
        return true;
    }


    public boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
                                       Integer maxAggrSize) {
        return groupCapacityRepository
            .findOne(QGroupCapacity.groupCapacity.groupId.eq(group))
            .map(groupCapacity -> {
                Optional.ofNullable(quota).ifPresent(v -> groupCapacity.setQuota(v));
                Optional.ofNullable(maxSize).ifPresent(v -> groupCapacity.setMaxSize(v));
                Optional.ofNullable(maxAggrCount).ifPresent(v -> groupCapacity.setMaxAggrCount(v));
                Optional.ofNullable(maxAggrSize).ifPresent(v -> groupCapacity.setMaxAggrSize(v));
                return groupCapacityRepository.save(groupCapacity);
            }).isPresent();
    }


    public boolean updateQuota(String group, Integer quota) {
        return updateGroupCapacity(group, quota, null, null, null);
    }


    public boolean correctUsage(String tenant, Timestamp gmtModified) {
        Long size = configInfoRepository.count(QConfigInfo.configInfo.tenantId.eq(tenant));
        Iterable<GroupCapacity> iterable = groupCapacityRepository.findAll(QTenantCapacity.tenantCapacity.tenantId.eq(tenant)
            .and(QTenantCapacity.tenantCapacity.gmtModified.eq(gmtModified)));
        iterable.forEach(tenantCapacity -> tenantCapacity.setUsage(size.intValue()));
        groupCapacityRepository.saveAll(iterable);
        return true;
    }

    /**
     * 获取GroupCapacity列表，只有id、groupId有值
     *
     * @param lastId   id > lastId
     * @param pageSize 页数
     * @return GroupCapacity列表
     */
    public List<GroupCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Page<GroupCapacity> page = groupCapacityRepository.findAll(qGroupCapacity.id.gt(lastId),
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        return page.getContent();
    }

//    public boolean updateMaxSize(String group, Integer maxSize) {
//        return updateGroupCapacity(group, null, maxSize, null, null);
//    }















    public boolean deleteGroupCapacity(final String group) {

        return true;
    }

}
