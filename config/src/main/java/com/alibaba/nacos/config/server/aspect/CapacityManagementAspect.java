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

package com.alibaba.nacos.config.server.aspect;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

/**
 * Capacity management aspect: batch write and update but don't process it.
 *
 * @author hexu.hxy
 * @date 2018/3/13
 */
@Aspect
public class CapacityManagementAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityManagementAspect.class);
    
    private static final String SYNC_UPDATE_CONFIG_ALL =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.publishConfig(..)) && args"
                    + "(request,response,dataId,group,content,appName,srcUser,tenant,tag,..)";
    
    private static final String DELETE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfig(..)) && args"
                    + "(request,response,dataId,group,tenant,..)";
    
    @Autowired
    private CapacityService capacityService;
    
    @Autowired
    private PersistService persistService;
    
    /**
     * Need to judge the size of content whether to exceed the limitation.
     */
    @Around(SYNC_UPDATE_CONFIG_ALL)
    public Object aroundSyncUpdateConfigAll(ProceedingJoinPoint pjp, HttpServletRequest request,
            HttpServletResponse response, String dataId, String group, String content, String appName, String srcUser,
            String tenant, String tag) throws Throwable {
        if (!PropertyUtil.isManageCapacity()) {
            return pjp.proceed();
        }
        LOGGER.info("[capacityManagement] aroundSyncUpdateConfigAll");
        String betaIps = request.getHeader("betaIps");
        if (StringUtils.isBlank(betaIps)) {
            if (StringUtils.isBlank(tag)) {
                // do capacity management limitation check for writing or updating config_info table.
                if (persistService.findConfigInfo(dataId, group, tenant) == null) {
                    // Write operation.
                    return do4Insert(pjp, request, response, group, tenant, content);
                }
                // Update operation.
                return do4Update(pjp, request, response, dataId, group, tenant, content);
            }
        }
        return pjp.proceed();
    }
    
    /**
     * Update operation: open the limitation of capacity management and it will check the size of content.
     *
     * @throws Throwable Throws Exception when actually operate.
     */
    private Object do4Update(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String dataId, String group, String tenant, String content) throws Throwable {
        if (!PropertyUtil.isCapacityLimitCheck()) {
            return pjp.proceed();
        }
        try {
            boolean hasTenant = hasTenant(tenant);
            Capacity capacity = getCapacity(group, tenant, hasTenant);
            if (isSizeLimited(group, tenant, getCurrentSize(content), hasTenant, false, capacity)) {
                return response4Limit(request, response, LimitType.OVER_MAX_SIZE);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] do4Update ", e);
        }
        return pjp.proceed();
    }
    
    /**
     * Write operation. Step 1: count whether to open the limitation checking function for capacity management; Step 2:
     * open limitation checking capacity management and check size of content and quota;
     *
     * @throws Throwable Exception.
     */
    private Object do4Insert(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String group, String tenant, String content) throws Throwable {
        LOGGER.info("[capacityManagement] do4Insert");
        CounterMode counterMode = CounterMode.INCREMENT;
        boolean hasTenant = hasTenant(tenant);
        if (PropertyUtil.isCapacityLimitCheck()) {
            // Write or update: usage + 1
            LimitType limitType = getLimitType(counterMode, group, tenant, content, hasTenant);
            if (limitType != null) {
                return response4Limit(request, response, limitType);
            }
        } else {
            // Write or update: usage + 1
            insertOrUpdateUsage(group, tenant, counterMode, hasTenant);
        }
        return getResult(pjp, response, group, tenant, counterMode, hasTenant);
    }
    
    private Object response4Limit(HttpServletRequest request, HttpServletResponse response, LimitType limitType) {
        response.setStatus(limitType.status);
        return String.valueOf(limitType.status);
    }
    
    private boolean hasTenant(String tenant) {
        return StringUtils.isNotBlank(tenant);
    }
    
    /**
     * The usage of capacity table for counting module will subtracte one whether open the limitation check of capacity
     * management.
     */
    @Around(DELETE_CONFIG)
    public Object aroundDeleteConfig(ProceedingJoinPoint pjp, HttpServletRequest request, HttpServletResponse response,
            String dataId, String group, String tenant) throws Throwable {
        if (!PropertyUtil.isManageCapacity()) {
            return pjp.proceed();
        }
        LOGGER.info("[capacityManagement] aroundDeleteConfig");
        ConfigInfo configInfo = persistService.findConfigInfo(dataId, group, tenant);
        if (configInfo == null) {
            return pjp.proceed();
        }
        return do4Delete(pjp, response, group, tenant, configInfo);
    }
    
    /**
     * Delete Operation.
     *
     * @throws Throwable Exception.
     */
    private Object do4Delete(ProceedingJoinPoint pjp, HttpServletResponse response, String group, String tenant,
            ConfigInfo configInfo) throws Throwable {
        boolean hasTenant = hasTenant(tenant);
        if (configInfo == null) {
            // "configInfo == null", has two possible points.
            // 1. Concurrently deletion.
            // 2. First, new sub configurations are added, and then all sub configurations are deleted.
            // At this time, the task (asynchronous) written to configinfo has not been executed.
            //
            // About 2 point, then it will execute to merge to write config_info's task orderly, and delete config_info's task.
            // Active modification of usage, when it happens to be in the above "merging to write config_info's task".
            // Modify usage when the task of info is finished, and usage = 1.
            // The following "delete config_info" task will not be executed with usage-1, because the request has already returned.
            // Therefore, it is necessary to modify the usage job regularly.
            correctUsage(group, tenant, hasTenant);
            return pjp.proceed();
        }
        
        // The same record can be deleted concurrently. This interface can be deleted asynchronously(submit MergeDataTask
        // to MergeTaskProcessor for processing), It may lead to more than one decrease in usage.
        // Therefore, it is necessary to modify the usage job regularly.
        CounterMode counterMode = CounterMode.DECREMENT;
        insertOrUpdateUsage(group, tenant, counterMode, hasTenant);
        return getResult(pjp, response, group, tenant, counterMode, hasTenant);
    }
    
    private void correctUsage(String group, String tenant, boolean hasTenant) {
        try {
            if (hasTenant) {
                LOGGER.info("[capacityManagement] correct usage, tenant: {}", tenant);
                capacityService.correctTenantUsage(tenant);
            } else {
                LOGGER.info("[capacityManagement] correct usage, group: {}", group);
                capacityService.correctGroupUsage(group);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] correctUsage ", e);
        }
    }
    
    private Object getResult(ProceedingJoinPoint pjp, HttpServletResponse response, String group, String tenant,
            CounterMode counterMode, boolean hasTenant) throws Throwable {
        try {
            // Execute operation actually.
            Object result = pjp.proceed();
            // Execute whether to callback based on the sql operation result.
            doResult(counterMode, response, group, tenant, result, hasTenant);
            return result;
        } catch (Throwable throwable) {
            LOGGER.warn("[capacityManagement] inner operation throw exception, rollback, group: {}, tenant: {}", group,
                    tenant, throwable);
            rollback(counterMode, group, tenant, hasTenant);
            throw throwable;
        }
    }
    
    /**
     * Usage counting service: it will count whether the limitation check function will be open.
     */
    private void insertOrUpdateUsage(String group, String tenant, CounterMode counterMode, boolean hasTenant) {
        try {
            capacityService.insertAndUpdateClusterUsage(counterMode, true);
            if (hasTenant) {
                capacityService.insertAndUpdateTenantUsage(counterMode, tenant, true);
            } else {
                capacityService.insertAndUpdateGroupUsage(counterMode, group, true);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] insertOrUpdateUsage ", e);
        }
    }
    
    private LimitType getLimitType(CounterMode counterMode, String group, String tenant, String content,
            boolean hasTenant) {
        try {
            boolean clusterLimited = !capacityService.insertAndUpdateClusterUsage(counterMode, false);
            if (clusterLimited) {
                LOGGER.warn("[capacityManagement] cluster capacity reaches quota.");
                return LimitType.OVER_CLUSTER_QUOTA;
            }
            if (content == null) {
                return null;
            }
            int currentSize = getCurrentSize(content);
            LimitType limitType = getGroupOrTenantLimitType(counterMode, group, tenant, currentSize, hasTenant);
            if (limitType != null) {
                rollbackClusterUsage(counterMode);
                return limitType;
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] isLimited ", e);
        }
        return null;
    }
    
    /**
     * Get and return the byte size of encoding.
     */
    private int getCurrentSize(String content) {
        try {
            return content.getBytes(Charset.forName(Constants.ENCODE)).length;
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] getCurrentSize ", e);
        }
        return 0;
    }
    
    private LimitType getGroupOrTenantLimitType(CounterMode counterMode, String group, String tenant, int currentSize,
            boolean hasTenant) {
        if (group == null) {
            return null;
        }
        Capacity capacity = getCapacity(group, tenant, hasTenant);
        if (isSizeLimited(group, tenant, currentSize, hasTenant, false, capacity)) {
            return LimitType.OVER_MAX_SIZE;
        }
        if (capacity == null) {
            insertCapacity(group, tenant, hasTenant);
        }
        boolean updateSuccess = isUpdateSuccess(counterMode, group, tenant, hasTenant);
        if (updateSuccess) {
            return null;
        }
        if (hasTenant) {
            return LimitType.OVER_TENANT_QUOTA;
        }
        return LimitType.OVER_GROUP_QUOTA;
    }
    
    private boolean isUpdateSuccess(CounterMode counterMode, String group, String tenant, boolean hasTenant) {
        boolean updateSuccess;
        if (hasTenant) {
            updateSuccess = capacityService.updateTenantUsage(counterMode, tenant);
            if (!updateSuccess) {
                LOGGER.warn("[capacityManagement] tenant capacity reaches quota, tenant: {}", tenant);
            }
        } else {
            updateSuccess = capacityService.updateGroupUsage(counterMode, group);
            if (!updateSuccess) {
                LOGGER.warn("[capacityManagement] group capacity reaches quota, group: {}", group);
            }
        }
        return updateSuccess;
    }
    
    private void insertCapacity(String group, String tenant, boolean hasTenant) {
        if (hasTenant) {
            capacityService.initTenantCapacity(tenant);
        } else {
            capacityService.initGroupCapacity(group);
        }
    }
    
    private Capacity getCapacity(String group, String tenant, boolean hasTenant) {
        Capacity capacity;
        if (hasTenant) {
            capacity = capacityService.getTenantCapacity(tenant);
        } else {
            capacity = capacityService.getGroupCapacity(group);
        }
        return capacity;
    }
    
    private boolean isSizeLimited(String group, String tenant, int currentSize, boolean hasTenant, boolean isAggr,
            Capacity capacity) {
        int defaultMaxSize = getDefaultMaxSize(isAggr);
        if (capacity != null) {
            Integer maxSize = getMaxSize(isAggr, capacity);
            if (maxSize == 0) {
                // If there exists capacity info and maxSize = 0, then it uses maxSize limitation default value to compare.
                return isOverSize(group, tenant, currentSize, defaultMaxSize, hasTenant);
            }
            // If there exists capacity info, then maxSize!=0.
            return isOverSize(group, tenant, currentSize, maxSize, hasTenant);
        }
        // If there no exists capacity info, then it uses maxSize limitation default value to compare.
        return isOverSize(group, tenant, currentSize, defaultMaxSize, hasTenant);
    }
    
    private Integer getMaxSize(boolean isAggr, Capacity capacity) {
        if (isAggr) {
            return capacity.getMaxAggrSize();
        }
        return capacity.getMaxSize();
    }
    
    private int getDefaultMaxSize(boolean isAggr) {
        if (isAggr) {
            return PropertyUtil.getDefaultMaxAggrSize();
        }
        return PropertyUtil.getDefaultMaxSize();
    }
    
    private boolean isOverSize(String group, String tenant, int currentSize, int maxSize, boolean hasTenant) {
        if (currentSize > maxSize) {
            if (hasTenant) {
                LOGGER.warn(
                        "[capacityManagement] tenant content is over maxSize, tenant: {}, maxSize: {}, currentSize: {}",
                        tenant, maxSize, currentSize);
            } else {
                LOGGER.warn(
                        "[capacityManagement] group content is over maxSize, group: {}, maxSize: {}, currentSize: {}",
                        group, maxSize, currentSize);
            }
            return true;
        }
        return false;
    }
    
    private void doResult(CounterMode counterMode, HttpServletResponse response, String group, String tenant,
            Object result, boolean hasTenant) {
        try {
            if (!isSuccess(response, result)) {
                LOGGER.warn(
                        "[capacityManagement] inner operation is fail, rollback, counterMode: {}, group: {}, tenant: {}",
                        counterMode, group, tenant);
                rollback(counterMode, group, tenant, hasTenant);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] doResult ", e);
        }
    }
    
    private boolean isSuccess(HttpServletResponse response, Object result) {
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_OK) {
            return true;
        }
        LOGGER.warn("[capacityManagement] response status is not 200, status: {}, result: {}", status, result);
        return false;
    }
    
    private void rollback(CounterMode counterMode, String group, String tenant, boolean hasTenant) {
        try {
            rollbackClusterUsage(counterMode);
            if (hasTenant) {
                capacityService.updateTenantUsage(counterMode.reverse(), tenant);
            } else {
                capacityService.updateGroupUsage(counterMode.reverse(), group);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] rollback ", e);
        }
    }
    
    private void rollbackClusterUsage(CounterMode counterMode) {
        try {
            if (!capacityService.updateClusterUsage(counterMode.reverse())) {
                LOGGER.error("[capacityManagement] cluster usage rollback fail counterMode: {}", counterMode);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] rollback ", e);
        }
    }
    
    /**
     * limit type.
     *
     * @author Nacos.
     */
    public enum LimitType {
        /**
         * over limit.
         */
        OVER_CLUSTER_QUOTA("超过集群配置个数上限", 429),
        OVER_GROUP_QUOTA("超过该Group配置个数上限", 429),
        OVER_TENANT_QUOTA("超过该租户配置个数上限", 429),
        OVER_MAX_SIZE("超过配置的内容大小上限", 429);
        
        public final String description;
        
        public final int status;
        
        LimitType(String description, int status) {
            this.description = description;
            this.status = status;
        }
    }
}
