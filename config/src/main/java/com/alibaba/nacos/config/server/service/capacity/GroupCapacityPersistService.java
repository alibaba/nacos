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

import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.modules.entity.GroupCapacityEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QGroupCapacityEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.GroupCapacityEntityMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.GroupCapacityMapStruct;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.GroupCapacityRepository;
import com.querydsl.core.BooleanBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;


/**
 * Group Capacity Service.
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class GroupCapacityPersistService {
    
    static final String CLUSTER = "";
    
    @Autowired
    private GroupCapacityRepository groupCapacityRepository;
    
    @Autowired
    private ConfigInfoRepository configInfoRepository;
    
    public GroupCapacity getGroupCapacity(String groupId) {
        GroupCapacityEntity groupCapacityEntity = groupCapacityRepository
                .findOne(QGroupCapacityEntity.groupCapacityEntity.groupId.eq(groupId)).orElse(null);
        return GroupCapacityMapStruct.INSTANCE.convertGroupCapacity(groupCapacityEntity);
    }
    
    public Capacity getClusterCapacity() {
        return getGroupCapacity(CLUSTER);
    }
    
    /**
     * Insert GroupCapacity into db.
     *
     * @param capacity capacity object instance.
     * @return operate result.
     */
    public boolean insertGroupCapacity(final GroupCapacity capacity) {
        Long configInfoSize;
        if (CLUSTER.equals(capacity.getGroup())) {
            configInfoSize = configInfoRepository.count();
        } else {
            BooleanBuilder booleanBuilder = new BooleanBuilder();
            QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
            booleanBuilder.and(qConfigInfo.groupId.eq(capacity.getGroup()));
            booleanBuilder.and(qConfigInfo.tenantId.isEmpty());
            configInfoSize = configInfoRepository.count(booleanBuilder);
        }
        return insertGroupCapacity(configInfoSize, capacity);
    }
    
    private boolean insertGroupCapacity(final Long configInfoSize, final GroupCapacity capacity) {
        capacity.setUsage(configInfoSize.intValue());
        GroupCapacityEntity groupCapacityEntity = GroupCapacityEntityMapStruct.INSTANCE
                .convertGroupCapacityEntity(capacity);
        groupCapacityRepository.save(groupCapacityEntity);
        return true;
    }
    
    public int getClusterUsage() {
        return 0;
    }
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity) {
        QGroupCapacityEntity qGroupCapacity = QGroupCapacityEntity.groupCapacityEntity;
        GroupCapacityEntity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroup())
                .and(qGroupCapacity.usage.lt(groupCapacity.getQuota())).and(qGroupCapacity.quota.eq(0))).orElse(null);
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
    
    /**
     * Increment UsageWithQuotaLimit.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        QGroupCapacityEntity qGroupCapacity = QGroupCapacityEntity.groupCapacityEntity;
        GroupCapacityEntity result = groupCapacityRepository
                .findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroup()).and(qGroupCapacity.quota.ne(0)))
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
    
    /**
     * Increment Usage.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean incrementUsage(GroupCapacity groupCapacity) {
        GroupCapacityEntity result = groupCapacityRepository
                .findOne(QGroupCapacityEntity.groupCapacityEntity.groupId.eq(groupCapacity.getGroup())).orElse(null);
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
    
    /**
     * Decrement Usage.
     *
     * @param groupCapacity groupCapacity object instance.
     * @return operate result.
     */
    public boolean decrementUsage(GroupCapacity groupCapacity) {
        QGroupCapacityEntity qGroupCapacity = QGroupCapacityEntity.groupCapacityEntity;
        GroupCapacityEntity result = groupCapacityRepository
                .findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroup()).and(qGroupCapacity.usage.gt(0)))
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
    
    /**
     * Update GroupCapacity.
     *
     * @param group        group string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return
     */
    public boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize) {
        return groupCapacityRepository.findOne(QGroupCapacityEntity.groupCapacityEntity.groupId.eq(group))
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
    
    public boolean updateMaxSize(String group, Integer maxSize) {
        return updateGroupCapacity(group, null, maxSize, null, null);
    }
    
    /**
     * Correct Usage.
     *
     * @param group       group string value.
     * @param gmtModified gmtModified.
     * @return operate result.
     */
    public boolean correctUsage(String group, Timestamp gmtModified) {
        Long size;
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        if (CLUSTER.equals(group)) {
            size = configInfoRepository.count(qConfigInfo.groupId.eq(group));
        } else {
            size = configInfoRepository.count(qConfigInfo.groupId.eq(group).and(qConfigInfo.tenantId.eq("")));
        }
        GroupCapacityEntity groupCapacity = groupCapacityRepository
                .findOne(QGroupCapacityEntity.groupCapacityEntity.groupId.eq(group)).orElse(null);
        if (groupCapacity == null) {
            return false;
        }
        groupCapacity.setUsage(size.intValue());
        groupCapacity.setGmtModified(gmtModified);
        groupCapacityRepository.save(groupCapacity);
        return true;
    }
    
    /**
     * Get group capacity list, noly has id and groupId value.
     *
     * @param lastId   lastId long value.
     * @param pageSize pageSize long value.
     * @return GroupCapacity list.
     */
    public List<GroupCapacity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        QGroupCapacityEntity qGroupCapacity = QGroupCapacityEntity.groupCapacityEntity;
        Page<GroupCapacityEntity> page = groupCapacityRepository.findAll(qGroupCapacity.id.gt(lastId),
                PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        return null;
    }
    
}
