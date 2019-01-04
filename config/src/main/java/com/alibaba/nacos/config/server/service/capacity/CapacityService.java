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

import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Capacity service
 *
 * @author hexu.hxy
 * @date 2018/03/05
 */
@Service
public class CapacityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityService.class);

    private static final Integer ZERO = 0;
    private static final int INIT_PAGE_SIZE = 500;

    @Autowired
    private GroupCapacityPersistService groupCapacityPersistService;
    @Autowired
    private TenantCapacityPersistService tenantCapacityPersistService;
    @Autowired
    private PersistService persistService;

    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public void init() {
        // 每个Server都有修正usage的Job在跑，幂等
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(
            "com.alibaba.nacos.CapacityManagement-%d").setDaemon(true).build();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("[capacityManagement] start correct usage");
                Stopwatch stopwatch = Stopwatch.createStarted();
                correctUsage();
                LOGGER.info("[capacityManagement] end correct usage, cost: {}s", stopwatch.elapsed(TimeUnit.SECONDS));

            }
        }, PropertyUtil.getCorrectUsageDelay(), PropertyUtil.getCorrectUsageDelay(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduledExecutorService.shutdown();
    }

    public void correctUsage() {
        correctGroupUsage();
        correctTenantUsage();
    }

    public void correctGroupUsage(String group) {
        groupCapacityPersistService.correctUsage(group, TimeUtils.getCurrentTime());
    }

    public void correctTenantUsage(String tenant) {
        tenantCapacityPersistService.correctUsage(tenant, TimeUtils.getCurrentTime());
    }

    public void initAllCapacity() {
        initAllCapacity(false);
        initAllCapacity(true);
    }

    private void initAllCapacity(boolean isTenant) {
        int page = 1;
        while (true) {
            List<String> list;
            if (isTenant) {
                list = persistService.getTenantIdList(page, INIT_PAGE_SIZE);
            } else {
                list = persistService.getGroupIdList(page, INIT_PAGE_SIZE);
            }
            for (String targetId : list) {
                if (isTenant) {
                    insertTenantCapacity(targetId);
                    autoExpansion(null, targetId);
                } else {
                    insertGroupCapacity(targetId);
                    autoExpansion(targetId, null);
                }
            }
            if (list.size() < INIT_PAGE_SIZE) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
            ++page;
        }
    }

    /**
     * 修正Group容量信息中的使用值（usage）
     */
    private void correctGroupUsage() {
        long lastId = 0;
        int pageSize = 100;
        while (true) {
            List<GroupCapacity> groupCapacityList = groupCapacityPersistService.getCapacityList4CorrectUsage(lastId,
                pageSize);
            if (groupCapacityList.isEmpty()) {
                break;
            }
            lastId = groupCapacityList.get(groupCapacityList.size() - 1).getId();
            for (GroupCapacity groupCapacity : groupCapacityList) {
                String group = groupCapacity.getGroup();
                groupCapacityPersistService.correctUsage(group, TimeUtils.getCurrentTime());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    /**
     * 修正Tenant容量信息中的使用值（usage）
     */
    private void correctTenantUsage() {
        long lastId = 0;
        int pageSize = 100;
        while (true) {
            List<TenantCapacity> tenantCapacityList = tenantCapacityPersistService.getCapacityList4CorrectUsage(lastId,
                pageSize);
            if (tenantCapacityList.isEmpty()) {
                break;
            }
            lastId = tenantCapacityList.get(tenantCapacityList.size() - 1).getId();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
            for (TenantCapacity tenantCapacity : tenantCapacityList) {
                String tenant = tenantCapacity.getTenant();
                tenantCapacityPersistService.correctUsage(tenant, TimeUtils.getCurrentTime());
            }
        }
    }

    /**
     * 集群：1. 如果容量信息不存在，则初始化容量信息<br/> 2. 更新容量的使用量usage，加一或减一
     *
     * @param counterMode      增加或者减少
     * @param ignoreQuotaLimit 是否忽略容量额度限制，在关闭容量管理的限制检验功能只计数的时候为true，开启容量管理的限制检验功能则为false
     * @return 是否操作成功
     */
    public boolean insertAndUpdateClusterUsage(CounterMode counterMode, boolean ignoreQuotaLimit) {
        Capacity capacity = groupCapacityPersistService.getClusterCapacity();
        if (capacity == null) {
            insertGroupCapacity(GroupCapacityPersistService.CLUSTER);
        }
        return updateGroupUsage(counterMode, GroupCapacityPersistService.CLUSTER,
            PropertyUtil.getDefaultClusterQuota(), ignoreQuotaLimit);
    }

    public boolean updateClusterUsage(CounterMode counterMode) {
        return updateGroupUsage(counterMode, GroupCapacityPersistService.CLUSTER,
            PropertyUtil.getDefaultClusterQuota(), false);
    }

    /**
     * 提供给关闭容量管理的限制检验功能时计数使用<br> Group：1. 如果容量信息不存在，则初始化容量信息<br/> 2. 更新容量的使用量usage，加一或减一
     *
     * @param counterMode      增加或者减少
     * @param group            group
     * @param ignoreQuotaLimit 是否忽略容量额度限制，在关闭容量管理的限制检验功能只计数的时候为true，开启容量管理的限制检验功能则为false
     * @return 是否操作成功
     */
    public boolean insertAndUpdateGroupUsage(CounterMode counterMode, String group, boolean ignoreQuotaLimit) {
        GroupCapacity groupCapacity = getGroupCapacity(group);
        if (groupCapacity == null) {
            initGroupCapacity(group, null, null, null, null);
        }
        return updateGroupUsage(counterMode, group, PropertyUtil.getDefaultGroupQuota(), ignoreQuotaLimit);
    }

    public GroupCapacity getGroupCapacity(String group) {
        return groupCapacityPersistService.getGroupCapacity(group);
    }

    public boolean updateGroupUsage(CounterMode counterMode, String group) {
        return updateGroupUsage(counterMode, group, PropertyUtil.getDefaultGroupQuota(), false);
    }

    /**
     * 初始化该Group的容量信息，如果到达限额，将自动扩容，以降低运维成本
     */
    public boolean initGroupCapacity(String group) {
        return initGroupCapacity(group, null, null, null, null);
    }

    /**
     * 初始化该Group的容量信息，如果到达限额，将自动扩容，以降低运维成本
     */
    private boolean initGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
                                      Integer maxAggrSize) {
        boolean insertSuccess = insertGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize);
        if (quota != null) {
            return insertSuccess;
        }
        autoExpansion(group, null);
        return insertSuccess;
    }

    /**
     * 自动扩容
     */
    private void autoExpansion(String group, String tenant) {
        Capacity capacity = getCapacity(group, tenant);
        int defaultQuota = getDefaultQuota(tenant != null);
        Integer usage = capacity.getUsage();
        if (usage < defaultQuota) {
            return;
        }
        // 初始化的时候该Group/租户就已经到达限额，自动扩容，降低运维成本
        int initialExpansionPercent = PropertyUtil.getInitialExpansionPercent();
        if (initialExpansionPercent > 0) {
            int finalQuota = (int)(usage + defaultQuota * (1.0 * initialExpansionPercent / 100));
            if (tenant != null) {
                tenantCapacityPersistService.updateQuota(tenant, finalQuota);
                LogUtil.defaultLog.warn("[capacityManagement] 初始化的时候该租户（{}）使用量（{}）就已经到达限额{}，自动扩容到{}", tenant,
                    usage, defaultQuota, finalQuota);
            } else {
                groupCapacityPersistService.updateQuota(group, finalQuota);
                LogUtil.defaultLog.warn("[capacityManagement] 初始化的时候该Group（{}）使用量（{}）就已经到达限额{}，自动扩容到{}", group,
                    usage, defaultQuota, finalQuota);
            }
        }
    }

    private int getDefaultQuota(boolean isTenant) {
        if (isTenant) {
            return PropertyUtil.getDefaultTenantQuota();
        }
        return PropertyUtil.getDefaultGroupQuota();
    }

    public Capacity getCapacity(String group, String tenant) {
        if (tenant != null) {
            return getTenantCapacity(tenant);
        }
        return getGroupCapacity(group);
    }

    public Capacity getCapacityWithDefault(String group, String tenant) {
        Capacity capacity;
        boolean isTenant = StringUtils.isNotBlank(tenant);
        if (isTenant) {
            capacity = getTenantCapacity(tenant);
        } else {
            capacity = getGroupCapacity(group);
        }
        if (capacity == null) {
            return null;
        }
        Integer quota = capacity.getQuota();
        if (quota == 0) {
            if (isTenant) {
                capacity.setQuota(PropertyUtil.getDefaultTenantQuota());
            } else {
                if (GroupCapacityPersistService.CLUSTER.equals(group)) {
                    capacity.setQuota(PropertyUtil.getDefaultClusterQuota());
                } else {
                    capacity.setQuota(PropertyUtil.getDefaultGroupQuota());
                }
            }
        }
        Integer maxSize = capacity.getMaxSize();
        if (maxSize == 0) {
            capacity.setMaxSize(PropertyUtil.getDefaultMaxSize());
        }
        Integer maxAggrCount = capacity.getMaxAggrCount();
        if (maxAggrCount == 0) {
            capacity.setMaxAggrCount(PropertyUtil.getDefaultMaxAggrCount());
        }
        Integer maxAggrSize = capacity.getMaxAggrSize();
        if (maxAggrSize == 0) {
            capacity.setMaxAggrSize(PropertyUtil.getDefaultMaxAggrSize());
        }
        return capacity;
    }

    public boolean initCapacity(String group, String tenant) {
        if (StringUtils.isNotBlank(tenant)) {
            return initTenantCapacity(tenant);
        }
        if (GroupCapacityPersistService.CLUSTER.equals(group)) {
            return insertGroupCapacity(GroupCapacityPersistService.CLUSTER);
        }
        // Group会自动扩容
        return initGroupCapacity(group);
    }

    private boolean insertGroupCapacity(String group) {
        return insertGroupCapacity(group, null, null, null, null);
    }

    private boolean insertGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
                                        Integer maxAggrSize) {
        try {
            final Timestamp now = TimeUtils.getCurrentTime();
            GroupCapacity groupCapacity = new GroupCapacity();
            groupCapacity.setGroup(group);
            // 新增时，quota=0表示限额为默认值，为了在更新默认限额时只需修改nacos配置，而不需要更新表中大部分数据
            groupCapacity.setQuota(quota == null ? ZERO : quota);
            // 新增时，maxSize=0表示大小为默认值，为了在更新默认大小时只需修改nacos配置，而不需要更新表中大部分数据
            groupCapacity.setMaxSize(maxSize == null ? ZERO : maxSize);
            groupCapacity.setMaxAggrCount(maxAggrCount == null ? ZERO : maxAggrCount);
            groupCapacity.setMaxAggrSize(maxAggrSize == null ? ZERO : maxAggrSize);
            groupCapacity.setGmtCreate(now);
            groupCapacity.setGmtModified(now);
            return groupCapacityPersistService.insertGroupCapacity(groupCapacity);
        } catch (DuplicateKeyException e) {
            // 并发情况下同时insert会出现，ignore
            LogUtil.defaultLog.warn("group: {}, message: {}", group, e.getMessage());
        }
        return false;
    }

    private boolean updateGroupUsage(CounterMode counterMode, String group, int defaultQuota,
                                     boolean ignoreQuotaLimit) {
        final Timestamp now = TimeUtils.getCurrentTime();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroup(group);
        groupCapacity.setQuota(defaultQuota);
        groupCapacity.setGmtModified(now);
        if (CounterMode.INCREMENT == counterMode) {
            if (ignoreQuotaLimit) {
                return groupCapacityPersistService.incrementUsage(groupCapacity);
            }
            // 先按默认值限额更新，大部分情况下都是默认值，默认值表里面的quota字段为0
            return groupCapacityPersistService.incrementUsageWithDefaultQuotaLimit(groupCapacity)
                || groupCapacityPersistService.incrementUsageWithQuotaLimit(groupCapacity);
        }
        return groupCapacityPersistService.decrementUsage(groupCapacity);
    }

    /**
     * 提供给关闭容量管理的限制检验功能时计数使用<br/> 租户： 1. 如果容量信息不存在，则初始化容量信息<br/> 2. 更新容量的使用量usage，加一或减一
     *
     * @param counterMode      增加或者减少
     * @param tenant           租户
     * @param ignoreQuotaLimit 是否忽略容量额度限制，在关闭容量管理的限制检验功能只计数的时候为true，开启容量管理的限制检验功能则为false
     * @return 是否操作成功
     */
    public boolean insertAndUpdateTenantUsage(CounterMode counterMode, String tenant, boolean ignoreQuotaLimit) {
        TenantCapacity tenantCapacity = getTenantCapacity(tenant);
        if (tenantCapacity == null) {
            // 初始化容量信息
            initTenantCapacity(tenant);
        }
        return updateTenantUsage(counterMode, tenant, ignoreQuotaLimit);
    }

    private boolean updateTenantUsage(CounterMode counterMode, String tenant, boolean ignoreQuotaLimit) {
        final Timestamp now = TimeUtils.getCurrentTime();
        TenantCapacity tenantCapacity = new TenantCapacity();
        tenantCapacity.setTenant(tenant);
        tenantCapacity.setQuota(PropertyUtil.getDefaultTenantQuota());
        tenantCapacity.setGmtModified(now);
        if (CounterMode.INCREMENT == counterMode) {
            if (ignoreQuotaLimit) {
                return tenantCapacityPersistService.incrementUsage(tenantCapacity);
            }
            // 先按默认值限额更新，大部分情况下都是默认值，默认值表里面的quota字段为0
            return tenantCapacityPersistService.incrementUsageWithDefaultQuotaLimit(tenantCapacity)
                || tenantCapacityPersistService.incrementUsageWithQuotaLimit(tenantCapacity);
        }
        return tenantCapacityPersistService.decrementUsage(tenantCapacity);
    }

    public boolean updateTenantUsage(CounterMode counterMode, String tenant) {
        return updateTenantUsage(counterMode, tenant, false);
    }

    /**
     * 初始化该租户的容量信息，如果到达限额，将自动扩容，以降低运维成本
     */
    public boolean initTenantCapacity(String tenant) {
        return initTenantCapacity(tenant, null, null, null, null);
    }

    /**
     * 初始化该租户的容量信息，如果到达限额，将自动扩容，以降低运维成本
     */
    public boolean initTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
                                      Integer maxAggrSize) {
        boolean insertSuccess = insertTenantCapacity(tenant, quota, maxSize, maxAggrCount, maxAggrSize);
        if (quota != null) {
            return insertSuccess;
        }
        autoExpansion(null, tenant);
        return insertSuccess;
    }

    private boolean insertTenantCapacity(String tenant) {
        return insertTenantCapacity(tenant, null, null, null, null);
    }

    private boolean insertTenantCapacity(String tenant, Integer quota, Integer maxSize, Integer maxAggrCount,
                                         Integer maxAggrSize) {
        try {
            final Timestamp now = TimeUtils.getCurrentTime();
            TenantCapacity tenantCapacity = new TenantCapacity();
            tenantCapacity.setTenant(tenant);
            // 新增时，quota=0表示限额为默认值，为了在更新默认限额时只需修改nacos配置，而不需要更新表中大部分数据
            tenantCapacity.setQuota(quota == null ? ZERO : quota);
            // 新增时，maxSize=0表示大小为默认值，为了在更新默认大小时只需修改nacos配置，而不需要更新表中大部分数据
            tenantCapacity.setMaxSize(maxSize == null ? ZERO : maxSize);
            tenantCapacity.setMaxAggrCount(maxAggrCount == null ? ZERO : maxAggrCount);
            tenantCapacity.setMaxAggrSize(maxAggrSize == null ? ZERO : maxAggrSize);
            tenantCapacity.setGmtCreate(now);
            tenantCapacity.setGmtModified(now);
            return tenantCapacityPersistService.insertTenantCapacity(tenantCapacity);
        } catch (DuplicateKeyException e) {
            // 并发情况下同时insert会出现，ignore
            LogUtil.defaultLog.warn("tenant: {}, message: {}", tenant, e.getMessage());
        }
        return false;
    }

    public TenantCapacity getTenantCapacity(String tenant) {
        return tenantCapacityPersistService.getTenantCapacity(tenant);
    }

    /**
     * 提供给API接口使用<br/> 租户：记录不存在则初始化，存在则直接更新容量限额或者内容大小
     *
     * @param group   Group ID
     * @param tenant  租户
     * @param quota   容量限额
     * @param maxSize 配置内容（content）大小限制
     * @return 是否操作成功
     */
    public boolean insertOrUpdateCapacity(String group, String tenant, Integer quota, Integer maxSize, Integer
        maxAggrCount, Integer maxAggrSize) {
        if (StringUtils.isNotBlank(tenant)) {
            Capacity capacity = tenantCapacityPersistService.getTenantCapacity(tenant);
            if (capacity == null) {
                return initTenantCapacity(tenant, quota, maxSize, maxAggrCount, maxAggrSize);
            }
            return tenantCapacityPersistService.updateTenantCapacity(tenant, quota, maxSize, maxAggrCount,
                maxAggrSize);
        }
        Capacity capacity = groupCapacityPersistService.getGroupCapacity(group);
        if (capacity == null) {
            return initGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize);
        }
        return groupCapacityPersistService.updateGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize);
    }
}
