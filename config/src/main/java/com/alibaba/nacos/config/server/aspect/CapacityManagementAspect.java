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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.CounterMode;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static com.alibaba.nacos.config.server.constant.Constants.LIMIT_ERROR_CODE;

/**
 * Capacity management aspect for config service.
 *
 * @author Nacos
 */
@Aspect
@Component
public class CapacityManagementAspect {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityManagementAspect.class);
    
    private static final String PUBLISH_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.publishConfig(..))";
    
    private static final String DELETE_CONFIG =
            "execution(* com.alibaba.nacos.config.server.service.ConfigOperationService.deleteConfig(..))";

    private final CapacityService capacityService;

    private final ConfigInfoPersistService configInfoPersistService;

    public CapacityManagementAspect(ConfigInfoPersistService configInfoPersistService, CapacityService capacityService) {
        this.configInfoPersistService = configInfoPersistService;
        this.capacityService = capacityService;
    }
    
    /**
     * Intercept publish config operations to perform capacity management checks.
     */
    @Around(PUBLISH_CONFIG)
    public Object aroundPublishConfig(ProceedingJoinPoint pjp) throws Throwable {
        if (!PropertyUtil.isManageCapacity()) {
            return pjp.proceed();
        }

        Object[] args = pjp.getArgs();
        ConfigForm configForm = (ConfigForm) args[0];
        ConfigRequestInfo configRequestInfo = (ConfigRequestInfo) args[1];
        String dataId = configForm.getDataId();
        String group = configForm.getGroup();
        String namespaceId = configForm.getNamespaceId();
        String content = configForm.getContent();
        String betaIps = configRequestInfo.getBetaIps();
        String tag = configForm.getTag();

        LOGGER.info("[CapacityManagement] Intercepting publishConfig operation for dataId: {}, group: {}, namespaceId: {}",
                dataId, group, namespaceId);

        if (StringUtils.isBlank(betaIps) && StringUtils.isBlank(tag) && StringUtils.isBlank(configForm.getGrayName())) {
            // do capacity management limitation check for writing or updating config_info table.
            if (configInfoPersistService.findConfigInfo(dataId, group, namespaceId) == null) {
                // Write operation.
                return do4Insert(pjp, group, namespaceId, content);
            } else {
                // Update operation.
                return do4Update(pjp, dataId, group, namespaceId, content);
            }
        }
        return pjp.proceed();
    }
    
    /**
     * Update operation: open the limitation of capacity management, and it will check the size of content.
     *
     * @throws Throwable Throws Exception when actually operate.
     */
    private Object do4Update(ProceedingJoinPoint pjp, String dataId, String group, String namespaceId, String content) throws Throwable {
        if (!PropertyUtil.isCapacityLimitCheck()) {
            return pjp.proceed();
        }
        try {
            boolean hasTenant = StringUtils.isNotBlank(namespaceId);
            Capacity capacity = getCapacity(group, namespaceId, hasTenant);
            if (isSizeLimited(group, namespaceId, getCurrentSize(content), hasTenant, false, capacity)) {
                throw new NacosException(ErrorCode.OVER_MAX_SIZE.getCode(),
                    String.format("Configuration content size limit exceeded [group=%s, namespaceId=%s].", group, namespaceId));
            }
        } catch (Exception e) {
            LOGGER.error("[CapacityManagement] Error during update operation for dataId: {}, group: {}, namespaceId: {}",
                    dataId, group, namespaceId, e);
            throw e;
        }
        return pjp.proceed();
    }
    
    /**
     * Write operation. Step 1: count whether to open the limitation checking function for capacity management; Step 2:
     * open limitation checking capacity management and check size of content and quota;
     *
     * @throws Throwable Exception.
     */
    private Object do4Insert(ProceedingJoinPoint pjp, String group, String namespaceId, String content) throws Throwable {
        LOGGER.info("[CapacityManagement] Handling insert operation for group: {}, namespaceId: {}", group, namespaceId);
        CounterMode counterMode = CounterMode.INCREMENT;
        boolean hasTenant = StringUtils.isNotBlank(namespaceId);
        
        if (PropertyUtil.isCapacityLimitCheck()) {
            // Write or update: usage + 1
            LimitType limitType = getLimitType(counterMode, group, namespaceId, content, hasTenant);
            if (limitType != null) {
                ErrorCode errorCode = ErrorCode.getErrorCode(limitType.name());
                if (errorCode != null) {
                    throw new NacosException(errorCode.getCode(),
                            String.format("Configuration limit exceeded [group=%s, namespaceId=%s].", group, namespaceId));
                }
            }
        } else {
            // Write or update: usage + 1
            insertOrUpdateUsage(group, namespaceId, counterMode, hasTenant);
        }
        return getResult(pjp, group, namespaceId, counterMode, hasTenant);
    }
    
    /**
     * Intercept delete config operations to perform capacity management checks.
     */
    @Around(DELETE_CONFIG)
    public Object aroundDeleteConfig(ProceedingJoinPoint pjp) throws Throwable {
        if (!PropertyUtil.isManageCapacity()) {
            return pjp.proceed();
        }
        
        Object[] args = pjp.getArgs();
        String dataId = (String) args[0];
        String group = (String) args[1];
        String namespaceId = (String) args[2];
        String grayName = (String) args[3];
        
        LOGGER.info("[CapacityManagement] Intercepting deleteConfig operation for dataId: {}, group: {}, namespaceId: {}", dataId, group,
                namespaceId);
        
        if (StringUtils.isNotBlank(grayName)) {
            return pjp.proceed();
        }
        
        ConfigInfo configInfo = configInfoPersistService.findConfigInfo(dataId, group, namespaceId);
        if (configInfo == null) {
            return pjp.proceed();
        }
        return do4Delete(pjp, group, namespaceId, configInfo);
    }
    
    /**
     * Delete Operation.
     *
     * @throws Throwable Exception.
     */
    private Object do4Delete(ProceedingJoinPoint pjp, String group, String namespaceId, ConfigInfo configInfo) throws Throwable {
        boolean hasTenant = StringUtils.isNotBlank(namespaceId);
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
            correctUsage(group, namespaceId, hasTenant);
            return pjp.proceed();
        }
        
        // The same record can be deleted concurrently. This interface can be deleted asynchronously(submit MergeDataTask
        // to MergeTaskProcessor for processing), It may lead to more than one decrease in usage.
        // Therefore, it is necessary to modify the usage job regularly.
        CounterMode counterMode = CounterMode.DECREMENT;
        insertOrUpdateUsage(group, namespaceId, counterMode, hasTenant);
        return getResult(pjp, group, namespaceId, counterMode, hasTenant);
    }
    
    private void correctUsage(String group, String namespaceId, boolean hasTenant) {
        try {
            if (hasTenant) {
                LOGGER.info("[capacityManagement] correct usage, namespaceId: {}", namespaceId);
                capacityService.correctTenantUsage(namespaceId);
            } else {
                LOGGER.info("[capacityManagement] correct usage, group: {}", group);
                capacityService.correctGroupUsage(group);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] correctUsage ", e);
        }
    }
    
    private Object getResult(ProceedingJoinPoint pjp, String group, String namespaceId, CounterMode counterMode, boolean hasTenant) throws Throwable {
        try {
            // Execute operation actually.
            Boolean result = (Boolean) pjp.proceed();
            if (!result) {
                rollbackUsage(counterMode, group, namespaceId, hasTenant);
            }
            return result;
        } catch (Throwable throwable) {
            LOGGER.warn("[capacityManagement] inner operation throw exception, rollback, group: {}, namespaceId: {}", group,
                    namespaceId, throwable);
            rollbackUsage(counterMode, group, namespaceId, hasTenant);
            throw throwable;
        }
    }
    
    /**
     * Usage counting service: it will count whether the limitation check function will be open.
     */
    private void insertOrUpdateUsage(String group, String namespaceId, CounterMode counterMode, boolean hasTenant) {
        try {
            capacityService.insertAndUpdateClusterUsage(counterMode, true);
            if (hasTenant) {
                capacityService.insertAndUpdateTenantUsage(counterMode, namespaceId, true);
            } else {
                capacityService.insertAndUpdateGroupUsage(counterMode, group, true);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] insertOrUpdateUsage ", e);
        }
    }
    
    private LimitType getLimitType(CounterMode counterMode, String group, String namespaceId, String content,
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
            LimitType limitType = getGroupOrTenantLimitType(counterMode, group, namespaceId, currentSize, hasTenant);
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
            return content.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] getCurrentSize ", e);
        }
        return 0;
    }
    
    private LimitType getGroupOrTenantLimitType(CounterMode counterMode, String group, String namespaceId, int currentSize,
            boolean hasTenant) {
        if (group == null) {
            return null;
        }
        Capacity capacity = getCapacity(group, namespaceId, hasTenant);
        if (isSizeLimited(group, namespaceId, currentSize, hasTenant, false, capacity)) {
            return LimitType.OVER_MAX_SIZE;
        }
        if (capacity == null) {
            insertCapacity(group, namespaceId, hasTenant);
        }
        boolean updateSuccess = isUpdateSuccess(counterMode, group, namespaceId, hasTenant);
        if (updateSuccess) {
            return null;
        }
        if (hasTenant) {
            return LimitType.OVER_TENANT_QUOTA;
        }
        return LimitType.OVER_GROUP_QUOTA;
    }
    
    private boolean isUpdateSuccess(CounterMode counterMode, String group, String namespaceId, boolean hasTenant) {
        boolean updateSuccess;
        if (hasTenant) {
            updateSuccess = capacityService.updateTenantUsage(counterMode, namespaceId);
            if (!updateSuccess) {
                LOGGER.warn("[capacityManagement] namespaceId capacity reaches quota, namespaceId: {}", namespaceId);
            }
        } else {
            updateSuccess = capacityService.updateGroupUsage(counterMode, group);
            if (!updateSuccess) {
                LOGGER.warn("[capacityManagement] group capacity reaches quota, group: {}", group);
            }
        }
        return updateSuccess;
    }
    
    private void insertCapacity(String group, String namespaceId, boolean hasTenant) {
        if (hasTenant) {
            capacityService.initTenantCapacity(namespaceId);
        } else {
            capacityService.initGroupCapacity(group);
        }
    }
    
    private Capacity getCapacity(String group, String namespaceId, boolean hasTenant) {
        Capacity capacity;
        if (hasTenant) {
            capacity = capacityService.getTenantCapacity(namespaceId);
        } else {
            capacity = capacityService.getGroupCapacity(group);
        }
        return capacity;
    }
    
    private boolean isSizeLimited(String group, String namespaceId, int currentSize, boolean hasTenant, boolean isAggr,
            Capacity capacity) {
        int defaultMaxSize = getDefaultMaxSize(isAggr);
        if (capacity != null) {
            Integer maxSize = getMaxSize(isAggr, capacity);
            if (maxSize == 0) {
                // If there exists capacity info and maxSize = 0, then it uses maxSize limitation default value to compare.
                return isOverSize(group, namespaceId, currentSize, defaultMaxSize, hasTenant);
            }
            // If there exists capacity info, then maxSize!=0.
            return isOverSize(group, namespaceId, currentSize, maxSize, hasTenant);
        }
        // If there no exists capacity info, then it uses maxSize limitation default value to compare.
        return isOverSize(group, namespaceId, currentSize, defaultMaxSize, hasTenant);
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
    
    private boolean isOverSize(String group, String namespaceId, int currentSize, int maxSize, boolean hasTenant) {
        if (currentSize > maxSize) {
            if (hasTenant) {
                LOGGER.warn(
                        "[capacityManagement] namespaceId content is over maxSize, namespaceId: {}, maxSize: {}, currentSize: {}",
                        namespaceId, maxSize, currentSize);
            } else {
                LOGGER.warn(
                        "[capacityManagement] group content is over maxSize, group: {}, maxSize: {}, currentSize: {}",
                        group, maxSize, currentSize);
            }
            return true;
        }
        return false;
    }
    
    private void rollbackUsage(CounterMode counterMode, String group, String namespaceId, boolean hasTenant) {
        try {
            rollbackClusterUsage(counterMode);
            if (hasTenant) {
                capacityService.updateTenantUsage(counterMode.reverse(), namespaceId);
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
        OVER_CLUSTER_QUOTA("Exceeded the maximum number of configurations in the cluster", LIMIT_ERROR_CODE),
        OVER_GROUP_QUOTA("Exceeded the maximum number of configurations in this group", LIMIT_ERROR_CODE),
        OVER_TENANT_QUOTA("Exceeded the maximum number of configurations for this namespaceId", LIMIT_ERROR_CODE),
        OVER_MAX_SIZE("Exceeded the maximum size limit of the configuration content", LIMIT_ERROR_CODE);
        
        public final String description;
        
        public final int status;
        
        LimitType(String description, int status) {
            this.description = description;
            this.status = status;
        }
    }
}
