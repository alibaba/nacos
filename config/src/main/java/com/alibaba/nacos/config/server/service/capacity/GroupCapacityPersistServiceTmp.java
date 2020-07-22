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
 * @author Nacos
 */
@Service
public class GroupCapacityPersistServiceTmp {

    static final String CLUSTER = "";

    @Autowired
    private GroupCapacityRepository groupCapacityRepository;

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    public GroupCapacityEntity getGroupCapacity(String groupId) {
        return groupCapacityRepository.findOne(QGroupCapacity.groupCapacity.groupId.eq(groupId))
            .orElse(null);
    }


    public CapacityEntity getClusterCapacity() {
        return getGroupCapacity(CLUSTER);
    }


    public boolean insertGroupCapacity(final GroupCapacityEntity capacity) {
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


    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacityEntity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        GroupCapacityEntity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
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

    private boolean insertGroupCapacity(final Long configInfoSize, final GroupCapacityEntity capacity) {
        capacity.setUsage(configInfoSize.intValue());
        groupCapacityRepository.save(capacity);
        return true;
    }

    public boolean incrementUsageWithQuotaLimit(GroupCapacityEntity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        GroupCapacityEntity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
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

    public boolean incrementUsage(GroupCapacityEntity groupCapacity) {
        GroupCapacityEntity result = groupCapacityRepository
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

    public boolean decrementUsage(GroupCapacityEntity groupCapacity) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        GroupCapacityEntity result = groupCapacityRepository.findOne(qGroupCapacity.groupId.eq(groupCapacity.getGroupId())
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
        GroupCapacityEntity groupCapacity = groupCapacityRepository.findOne(QGroupCapacity.groupCapacity.groupId.eq(group))
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
    public List<GroupCapacityEntity> getCapacityList4CorrectUsage(long lastId, int pageSize) {
        QGroupCapacity qGroupCapacity = QGroupCapacity.groupCapacity;
        Page<GroupCapacityEntity> page = groupCapacityRepository.findAll(qGroupCapacity.id.gt(lastId),
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        return page.getContent();
    }

}
