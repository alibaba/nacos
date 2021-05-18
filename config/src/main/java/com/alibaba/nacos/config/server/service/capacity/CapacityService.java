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
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Capacity service.
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
    
    /**
     * Init.
     */
    @PostConstruct
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public void init() {
        // All servers have jobs that modify usage, idempotent.
        ConfigExecutor.scheduleCorrectUsageTask(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("[capacityManagement] start correct usage");
                Stopwatch stopwatch = Stopwatch.createStarted();
                correctUsage();
                LOGGER.info("[capacityManagement] end correct usage, cost: {}s", stopwatch.elapsed(TimeUnit.SECONDS));
                
            }
        }, PropertyUtil.getCorrectUsageDelay(), PropertyUtil.getCorrectUsageDelay(), TimeUnit.SECONDS);
    }
    
    public void correctUsage() {
        correctGroupUsage();
        correctTenantUsage();
    }
    
    /**
     * Correct the usage of group capacity.
     */
    private void correctGroupUsage() {
        long lastId = 0;
        int pageSize = 100;
        while (true) {
            List<GroupCapacity> groupCapacityList = groupCapacityPersistService
                    .getCapacityList4CorrectUsage(lastId, pageSize);
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
    
    public void correctGroupUsage(String group) {
        groupCapacityPersistService.correctUsage(group, TimeUtils.getCurrentTime());
    }
    
    public void correctTenantUsage(String tenant) {
        tenantCapacityPersistService.correctUsage(tenant, TimeUtils.getCurrentTime());
    }
    
    /**
     * Correct the usage of group capacity.
     */
    private void correctTenantUsage() {
        long lastId = 0;
        int pageSize = 100;
        while (true) {
            List<TenantCapacity> tenantCapacityList = tenantCapacityPersistService
                    .getCapacityList4CorrectUsage(lastId, pageSize);
            if (tenantCapacityList.isEmpty()) {
                break;
            }
            lastId = tenantCapacityList.get(tenantCapacityList.size() - 1).getId();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            for (TenantCapacity tenantCapacity : tenantCapacityList) {
                String tenant = tenantCapacity.getTenant();
                tenantCapacityPersistService.correctUsage(tenant, TimeUtils.getCurrentTime());
            }
        }
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
            } catch (InterruptedException ignored) {
            }
            ++page;
        }
    }
    
    /**
     * To Cluster. 1.If the capacity information does not exist, initialize the capacity information. 2.Update capacity
     * usage, plus or minus one.
     *
     * @param counterMode      increase or decrease mode.
     * @param ignoreQuotaLimit ignoreQuotaLimit flag.
     * @return the result of update cluster usage.
     */
    public boolean insertAndUpdateClusterUsage(CounterMode counterMode, boolean ignoreQuotaLimit) {
        Capacity capacity = groupCapacityPersistService.getClusterCapacity();
        if (capacity == null) {
            insertGroupCapacity(GroupCapacityPersistService.CLUSTER);
        }
        return updateGroupUsage(counterMode, GroupCapacityPersistService.CLUSTER, PropertyUtil.getDefaultClusterQuota(),
                ignoreQuotaLimit);
    }
    
    public boolean updateClusterUsage(CounterMode counterMode) {
        return updateGroupUsage(counterMode, GroupCapacityPersistService.CLUSTER, PropertyUtil.getDefaultClusterQuota(),
                false);
    }
    
    /**
     * It is used for counting when the limit check function of capacity management is turned off. 1.If the capacity
     * information does not exist, initialize the capacity information. 2.Update capacity usage, plus or minus one.
     *
     * @param counterMode      increase or decrease mode.
     * @param group            tenant string value.
     * @param ignoreQuotaLimit ignoreQuotaLimit flag.
     * @return operate successfully or not.
     */
    public boolean insertAndUpdateGroupUsage(CounterMode counterMode, String group, boolean ignoreQuotaLimit) {
        GroupCapacity groupCapacity = getGroupCapacity(group);
        if (groupCapacity == null) {
            initGroupCapacity(group, null, null, null, null);
        }
        return updateGroupUsage(counterMode, group, PropertyUtil.getDefaultGroupQuota(), ignoreQuotaLimit);
    }
    
    public boolean updateGroupUsage(CounterMode counterMode, String group) {
        return updateGroupUsage(counterMode, group, PropertyUtil.getDefaultGroupQuota(), false);
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
            // First update the quota according to the default value. In most cases, it is the default value.
            // The quota field in the default value table is 0
            return groupCapacityPersistService.incrementUsageWithDefaultQuotaLimit(groupCapacity)
                    || groupCapacityPersistService.incrementUsageWithQuotaLimit(groupCapacity);
        }
        return groupCapacityPersistService.decrementUsage(groupCapacity);
    }
    
    public GroupCapacity getGroupCapacity(String group) {
        return groupCapacityPersistService.getGroupCapacity(group);
    }
    
    /**
     * Initialize the capacity information of the group. If the quota is reached, the capacity will be automatically
     * expanded to reduce the operation and maintenance cost.
     *
     * @param group group string value.
     * @return init result.
     */
    public boolean initGroupCapacity(String group) {
        return initGroupCapacity(group, null, null, null, null);
    }
    
    /**
     * Initialize the capacity information of the group. If the quota is reached, the capacity will be automatically
     * expanded to reduce the operation and maintenance cost.
     *
     * @param group        group string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return init result.
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
     * Expand capacity automatically.
     *
     * @param group  group string value.
     * @param tenant tenant string value.
     */
    private void autoExpansion(String group, String tenant) {
        Capacity capacity = getCapacity(group, tenant);
        int defaultQuota = getDefaultQuota(tenant != null);
        Integer usage = capacity.getUsage();
        if (usage < defaultQuota) {
            return;
        }
        // Initialize the capacity information of the group. If the quota is reached,
        // the capacity will be automatically expanded to reduce the operation and maintenance cost.
        int initialExpansionPercent = PropertyUtil.getInitialExpansionPercent();
        if (initialExpansionPercent > 0) {
            int finalQuota = (int) (usage + defaultQuota * (1.0 * initialExpansionPercent / 100));
            if (tenant != null) {
                tenantCapacityPersistService.updateQuota(tenant, finalQuota);
                LogUtil.DEFAULT_LOG.warn("[capacityManagement] The usage({}) already reach the upper limit({}) when init the tenant({}), "
                        + "automatic upgrade to ({})", usage, defaultQuota, tenant, finalQuota);
            } else {
                groupCapacityPersistService.updateQuota(group, finalQuota);
                LogUtil.DEFAULT_LOG.warn("[capacityManagement] The usage({}) already reach the upper limit({}) when init the group({}), "
                        + "automatic upgrade to ({})", usage, defaultQuota, group, finalQuota);
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
    
    /**
     * Init capacity.
     *
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return init result.
     */
    public boolean initCapacity(String group, String tenant) {
        if (StringUtils.isNotBlank(tenant)) {
            return initTenantCapacity(tenant);
        }
        if (GroupCapacityPersistService.CLUSTER.equals(group)) {
            return insertGroupCapacity(GroupCapacityPersistService.CLUSTER);
        }
        // Group can expand capacity automatically.
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
            // When adding a new quota, quota = 0 means that the quota is the default value.
            // In order to update the default quota, only the Nacos configuration needs to be modified,
            // and most of the data in the table need not be updated.
            groupCapacity.setQuota(quota == null ? ZERO : quota);
            
            // When adding new data, maxsize = 0 means that the size is the default value.
            // In order to update the default size, you only need to modify the Nacos configuration without updating most of the data in the table.
            groupCapacity.setMaxSize(maxSize == null ? ZERO : maxSize);
            groupCapacity.setMaxAggrCount(maxAggrCount == null ? ZERO : maxAggrCount);
            groupCapacity.setMaxAggrSize(maxAggrSize == null ? ZERO : maxAggrSize);
            groupCapacity.setGmtCreate(now);
            groupCapacity.setGmtModified(now);
            return groupCapacityPersistService.insertGroupCapacity(groupCapacity);
        } catch (DuplicateKeyException e) {
            // this exception will meet when concurrent insert，ignore it
            LogUtil.DEFAULT_LOG.warn("group: {}, message: {}", group, e.getMessage());
        }
        return false;
    }
    
    /**
     * It is used for counting when the limit check function of capacity management is turned off. 1.If the capacity
     * information does not exist, initialize the capacity information. 2.Update capacity usage, plus or minus one.
     *
     * @param counterMode      increase or decrease mode.
     * @param tenant           tenant string value.
     * @param ignoreQuotaLimit ignoreQuotaLimit flag.
     * @return operate successfully or not.
     */
    public boolean insertAndUpdateTenantUsage(CounterMode counterMode, String tenant, boolean ignoreQuotaLimit) {
        TenantCapacity tenantCapacity = getTenantCapacity(tenant);
        if (tenantCapacity == null) {
            // Init capacity information.
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
            // First update the quota according to the default value. In most cases, it is the default value.
            // The quota field in the default value table is 0.
            return tenantCapacityPersistService.incrementUsageWithDefaultQuotaLimit(tenantCapacity)
                    || tenantCapacityPersistService.incrementUsageWithQuotaLimit(tenantCapacity);
        }
        return tenantCapacityPersistService.decrementUsage(tenantCapacity);
    }
    
    public boolean updateTenantUsage(CounterMode counterMode, String tenant) {
        return updateTenantUsage(counterMode, tenant, false);
    }
    
    /**
     * Initialize the capacity information of the tenant. If the quota is reached, the capacity will be automatically
     * expanded to reduce the operation and maintenance cos.
     *
     * @param tenant tenant string value.
     * @return init result.
     */
    public boolean initTenantCapacity(String tenant) {
        return initTenantCapacity(tenant, null, null, null, null);
    }
    
    /**
     * Initialize the capacity information of the tenant. If the quota is reached, the capacity will be automatically
     * expanded to reduce the operation and maintenance cost
     *
     * @param tenant       tenant string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return
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
            // When adding a new quota, quota = 0 means that the quota is the default value.
            // In order to update the default quota, only the Nacos configuration needs to be modified,
            // and most of the data in the table need not be updated.
            tenantCapacity.setQuota(quota == null ? ZERO : quota);
            
            // When adding new data, maxsize = 0 means that the size is the default value.
            // In order to update the default size, you only need to modify the Nacos configuration without updating most of the data in the table.
            tenantCapacity.setMaxSize(maxSize == null ? ZERO : maxSize);
            tenantCapacity.setMaxAggrCount(maxAggrCount == null ? ZERO : maxAggrCount);
            tenantCapacity.setMaxAggrSize(maxAggrSize == null ? ZERO : maxAggrSize);
            tenantCapacity.setGmtCreate(now);
            tenantCapacity.setGmtModified(now);
            return tenantCapacityPersistService.insertTenantCapacity(tenantCapacity);
        } catch (DuplicateKeyException e) {
            // this exception will meet when concurrent insert，ignore it
            LogUtil.DEFAULT_LOG.warn("tenant: {}, message: {}", tenant, e.getMessage());
        }
        return false;
    }
    
    public TenantCapacity getTenantCapacity(String tenant) {
        return tenantCapacityPersistService.getTenantCapacity(tenant);
    }
    
    /**
     * Support for API interface, Tenant: initialize if the record does not exist, and update the capacity quota or
     * content size directly if it exists.
     *
     * @param group        group string value.
     * @param tenant       tenant string value.
     * @param quota        quota int value.
     * @param maxSize      maxSize int value.
     * @param maxAggrCount maxAggrCount int value.
     * @param maxAggrSize  maxAggrSize int value.
     * @return operate successfully or not.
     */
    public boolean insertOrUpdateCapacity(String group, String tenant, Integer quota, Integer maxSize,
            Integer maxAggrCount, Integer maxAggrSize) {
        if (StringUtils.isNotBlank(tenant)) {
            Capacity capacity = tenantCapacityPersistService.getTenantCapacity(tenant);
            if (capacity == null) {
                return initTenantCapacity(tenant, quota, maxSize, maxAggrCount, maxAggrSize);
            }
            return tenantCapacityPersistService.updateTenantCapacity(tenant, quota, maxSize, maxAggrCount, maxAggrSize);
        }
        Capacity capacity = groupCapacityPersistService.getGroupCapacity(group);
        if (capacity == null) {
            return initGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize);
        }
        return groupCapacityPersistService.updateGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize);
    }
}
