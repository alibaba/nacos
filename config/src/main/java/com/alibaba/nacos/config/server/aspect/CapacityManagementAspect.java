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
import com.alibaba.nacos.config.server.service.PersistService;
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
 * 容量管理切面：批量写入、更新暂不处理
 *
 * @author hexu.hxy
 * @date 2018/3/13
 */
@Aspect
public class CapacityManagementAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityManagementAspect.class);

    private static final String SYNC_UPDATE_CONFIG_ALL
        = "execution(* com.alibaba.nacos.config.server.controller.ConfigController.publishConfig(..)) && args"
        + "(request,response,dataId,group,content,appName,srcUser,tenant,tag,..)";

    private static final String DELETE_CONFIG
        = "execution(* com.alibaba.nacos.config.server.controller.ConfigController.deleteConfig(..)) && args"
        + "(request,response,dataId,group,tenant,..)";

    @Autowired
    private CapacityService capacityService;
    @Autowired
    private PersistService persistService;

    /**
     * 更新也需要判断content内容是否超过大小限制
     */
    @Around(SYNC_UPDATE_CONFIG_ALL)
    public Object aroundSyncUpdateConfigAll(ProceedingJoinPoint pjp, HttpServletRequest request,
                                            HttpServletResponse response, String dataId, String group, String content,
                                            String appName, String srcUser, String tenant, String tag)
        throws Throwable {
        if (!PropertyUtil.isManageCapacity()) {
            return pjp.proceed();
        }
        LOGGER.info("[capacityManagement] aroundSyncUpdateConfigAll");
        String betaIps = request.getHeader("betaIps");
        if (StringUtils.isBlank(betaIps)) {
            if (StringUtils.isBlank(tag)) {
                // 只对写入或更新config_info表的做容量管理的限制检验
                if (persistService.findConfigInfo(dataId, group, tenant) == null) {
                    // 写入操作
                    return do4Insert(pjp, request, response, group, tenant, content);
                }
                // 更新操作
                return do4Update(pjp, request, response, dataId, group, tenant, content);
            }
        }
        return pjp.proceed();
    }

    /**
     * 更新操作：开启容量管理的限制检验功能，会检验"content的大小"是否超过限制
     *
     * @throws Throwable "实际操作"抛出的异常
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
     * 写入操作：1. 无论是否开启容量管理的限制检验功能都会计数（usage） 2.开启容量管理的限制检验功能，会检验"限额"和"content的大小"
     *
     * @throws Throwable "实际操作"抛出的异常
     */
    private Object do4Insert(ProceedingJoinPoint pjp, HttpServletRequest request,
                             HttpServletResponse response, String group, String tenant, String content)
        throws Throwable {
        LOGGER.info("[capacityManagement] do4Insert");
        CounterMode counterMode = CounterMode.INCREMENT;
        boolean hasTenant = hasTenant(tenant);
        if (PropertyUtil.isCapacityLimitCheck()) {
            // 先写入或更新：usage + 1
            LimitType limitType = getLimitType(counterMode, group, tenant, content, hasTenant);
            if (limitType != null) {
                return response4Limit(request, response, limitType);
            }
        } else {
            // 先写入或更新：usage + 1
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
     * 无论是否开启容量管理的限制检验功能，删除时候，计数模块中容量信息表中的usage都得减一
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
     * @throws Throwable "实际操作"抛出的异常
     */
    private Object do4Delete(ProceedingJoinPoint pjp, HttpServletResponse response, String group, String tenant,
                             ConfigInfo configInfo)
        throws Throwable {
        boolean hasTenant = hasTenant(tenant);
        if (configInfo == null) {
            // "configInfo == null"有2种可能：
            // 1. 并发删除；2. 先是新增子配置，后来删除了所有子配置，这时合并写入到configInfo的task（异步)还没执行
            // 关于第2点，那么接下会顺序执行"合并写入config_info的task"，"删除config_info的task"
            // 主动修正usage，当刚好在上述的"合并写入config_info的task"执行完时修正usage，此时usage=1
            // 而后面个"删除config_info的task"执行时并不会把usage-1，因为请求已经返回了。
            // 因此还是需要定时修正usage的Job
            correctUsage(group, tenant, hasTenant);
            return pjp.proceed();
        }
        // 并发删除同一个记录，可能同时走到这里，加上这个接口是异步删除的（提交MergeDataTask给MergeTaskProcessor处理），可能导致usage不止减一。因此还是需要定时修正usage的Job
        CounterMode counterMode = CounterMode.DECREMENT;
        insertOrUpdateUsage(group, tenant, counterMode, hasTenant);
        return getResult(pjp, response, group, tenant, counterMode, hasTenant);
    }

    private void correctUsage(String group, String tenant, boolean hasTenant) {
        try {
            if (hasTenant) {
                LOGGER.info("主动修正usage, tenant: {}", tenant);
                capacityService.correctTenantUsage(tenant);
            } else {
                LOGGER.info("主动修正usage, group: {}", group);
                capacityService.correctGroupUsage(group);
            }
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] correctUsage ", e);
        }
    }

    private Object getResult(ProceedingJoinPoint pjp, HttpServletResponse response, String group, String tenant,
                             CounterMode counterMode, boolean hasTenant) throws Throwable {
        try {
            // 执行实际操作
            Object result = pjp.proceed();
            // 根据执行结果判定是否需要回滚
            doResult(counterMode, response, group, tenant, result, hasTenant);
            return result;
        } catch (Throwable throwable) {
            LOGGER.warn("[capacityManagement] inner operation throw exception, rollback, group: {}, tenant: {}",
                group, tenant, throwable);
            rollback(counterMode, group, tenant, hasTenant);
            throw throwable;
        }
    }

    /**
     * usage计数器服务：无论容量管理的限制检验功能是否开启，都会进行计数
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

    private LimitType getLimitType(CounterMode counterMode, String group, String tenant, String content, boolean
        hasTenant) {
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
            LimitType limitType = getGroupOrTenantLimitType(counterMode, group, tenant, currentSize,
                hasTenant);
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
     * 编码字节数
     */
    private int getCurrentSize(String content) {
        try {
            return content.getBytes(Charset.forName(Constants.ENCODE)).length;
        } catch (Exception e) {
            LOGGER.error("[capacityManagement] getCurrentSize ", e);
        }
        return 0;
    }

    private LimitType getGroupOrTenantLimitType(CounterMode counterMode, String group, String tenant,
                                                int currentSize, boolean hasTenant) {
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
                // 已经存在容量信息记录，maxSize=0，则使用"默认maxSize限制值"进行比较
                return isOverSize(group, tenant, currentSize, defaultMaxSize, hasTenant);
            }
            // 已经存在容量信息记录，maxSize!=0
            return isOverSize(group, tenant, currentSize, maxSize, hasTenant);
        }
        // 不已经存在容量信息记录，使用"默认maxSize限制值"进行比较
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

    private void doResult(CounterMode counterMode, HttpServletResponse response, String group,
                          String tenant, Object result, boolean hasTenant) {
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
        LOGGER.warn("[capacityManagement] response status is not 200, status: {}, result: {}", status,
            result);
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
     * limit tyep
     *
     * @author Nacos
     */
    public enum LimitType {
        /**
         * over limit
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
