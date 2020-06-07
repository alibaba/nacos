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

    static final String CLUSTER = "";

    @Autowired
    private GroupCapacityRepository groupCapacityRepository;

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    public GroupCapacity getGroupCapacity(String groupId) {
        return groupCapacityRepository.findOne(QGroupCapacity.groupCapacity.groupId.eq(groupId))
            .orElse(null);
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
        GroupCapacity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
            .and(qGroupCapacity.usage.lt(groupCapacity.getQuota()))
            .and(qGroupCapacity.quota.eq(0)))
            .orElse(null);
        if (result == null) {
            return false;
        }
        result.setGmtModified(groupCapacity.getGmtModified());
        if (result.getUsage() == null) {
            result.setUsage(1);
        } else {
            result.setUsage(result.getUsage() + 1);
        }
        groupCapacityRepository.save(result);
        return true;
    }

    private boolean insertGroupCapacity(final Long configInfoSize, final GroupCapacity capacity) {
        capacity.setUsage(configInfoSize.intValue());
        groupCapacityRepository.save(capacity);
        return true;
    }

    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        GroupCapacity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
            .and(qGroupCapacity.quota.ne(0)))
            .orElse(null);
        if (result == null) {
            return false;
        }
        //usage 需要小于quota
        if (result.getUsage() >= result.getQuota()) {
            return false;
        }
        result.setUsage(result.getUsage() + 1);
        result.setGmtModified(groupCapacity.getGmtModified());
        groupCapacityRepository.save(result);
        return true;
    }

    public boolean incrementUsage(GroupCapacity groupCapacity) {
        GroupCapacity result = groupCapacityRepository
            .findOne(QGroupCapacity.groupCapacity.groupId.eq(groupCapacity.getGroupId()))
            .orElse(null);
        if (result == null) {
            return false;
        }
        if (result.getUsage() == null) {
            result.setUsage(1);
        } else {
            result.setUsage(result.getUsage() + 1);
        }
        result.setGmtModified(groupCapacity.getGmtModified());
        groupCapacityRepository.save(result);
        return true;
    }

    public boolean decrementUsage(GroupCapacity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        GroupCapacity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
            .and(qGroupCapacity.usage.gt(0)))
            .orElse(null);
        if (result == null) {
            return false;
        }
        if (result.getUsage() != null && result.getUsage() > 0) {
            result.setUsage(result.getUsage() - 1);
        }
        result.setGmtModified(groupCapacity.getGmtModified());
        groupCapacityRepository.save(result);
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


    public boolean correctUsage(String group, Timestamp gmtModified) {
        Long size;
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        if (CLUSTER.equals(group)) {
            size = configInfoRepository.count(qConfigInfo.groupId.eq(group));
        } else {
            size = configInfoRepository.count(qConfigInfo.groupId.eq(group)
                .and(qConfigInfo.tenantId.eq("")));
        }
        GroupCapacity groupCapacity = groupCapacityRepository.findOne(QGroupCapacity.groupCapacity.groupId.eq(group))
            .orElse(null);
        if (groupCapacity == null) {
            return false;
        }
        groupCapacity.setUsage(size.intValue());
        groupCapacity.setGmtModified(gmtModified);
        groupCapacityRepository.save(groupCapacity);
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

}
