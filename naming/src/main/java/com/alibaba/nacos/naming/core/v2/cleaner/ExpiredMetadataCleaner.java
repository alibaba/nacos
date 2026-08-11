/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.cleaner;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ExpiredMetadataInfo;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Expired metadata cleaner.
 *
 * @author xiweng.yy
 */
@Component
public class ExpiredMetadataCleaner extends AbstractNamingCleaner {
    
    private static final String EXPIRED_METADATA = "expiredMetadata";
    
    private static final int INITIAL_DELAY = 5000;
    
    private final NamingMetadataManager metadataManager;
    
    private final NamingMetadataOperateService metadataOperateService;
    
    private final ServiceStorage serviceStorage;
    
    public ExpiredMetadataCleaner(NamingMetadataManager metadataManager,
        NamingMetadataOperateService metadataOperateService, ServiceStorage serviceStorage) {
        this.metadataManager = metadataManager;
        this.metadataOperateService = metadataOperateService;
        this.serviceStorage = serviceStorage;
        GlobalExecutor.scheduleExpiredClientCleaner(this, INITIAL_DELAY,
            GlobalConfig.getExpiredMetadataCleanInterval(),
            TimeUnit.MILLISECONDS);
    }
    
    @Override
    public String getType() {
        return EXPIRED_METADATA;
    }
    
    @Override
    public void doClean() {
        long currentTime = System.currentTimeMillis();
        for (ExpiredMetadataInfo each : metadataManager.getExpiredMetadataInfos()) {
            if (currentTime - each.getCreateTime() > GlobalConfig.getExpiredMetadataExpiredTime()) {
                removeExpiredMetadata(each);
            }
        }
    }
    
    private void removeExpiredMetadata(ExpiredMetadataInfo expiredInfo) {
        if (null == expiredInfo.getMetadataId()) {
            Loggers.SRV_LOG.info("Remove expired metadata {}", expiredInfo);
            if (metadataManager.containServiceMetadata(expiredInfo.getService())) {
                metadataOperateService.deleteServiceMetadata(expiredInfo.getService());
            }
        } else {
            if (isInstanceStillRegistered(expiredInfo)) {
                // The metadata id is derived from ip:port:cluster rather than from the client id,
                // so the same instance can be re-registered by another client after the original
                // client is expired. In that case the metadata is still in use, keep it and stop
                // tracking this expired record.
                Loggers.SRV_LOG.info("Instance is still registered, keep metadata {}", expiredInfo);
                metadataManager.getExpiredMetadataInfos().remove(expiredInfo);
                return;
            }
            Loggers.SRV_LOG.info("Remove expired metadata {}", expiredInfo);
            if (metadataManager.containInstanceMetadata(expiredInfo.getService(),
                expiredInfo.getMetadataId())) {
                metadataOperateService.deleteInstanceMetadata(expiredInfo.getService(),
                    expiredInfo.getMetadataId());
            }
        }
    }
    
    /**
     * Check whether the instance owning the expired metadata is still registered in its service.
     *
     * <p>The metadata id is rebuilt from each published instance instead of being parsed back from
     * the expired metadata id, so that IPv6 addresses containing colons are handled correctly.</p>
     *
     * @param expiredInfo expired instance metadata info
     * @return {@code true} if the instance is still registered
     */
    private boolean isInstanceStillRegistered(ExpiredMetadataInfo expiredInfo) {
        Service service = expiredInfo.getService();
        ServiceInfo serviceInfo = serviceStorage.getPushData(service);
        if (null == serviceInfo || null == serviceInfo.getHosts()) {
            return false;
        }
        for (Instance each : serviceInfo.getHosts()) {
            String metadataId = InstancePublishInfo
                .genMetadataId(each.getIp(), each.getPort(), each.getClusterName());
            if (metadataId.equals(expiredInfo.getMetadataId())) {
                return true;
            }
        }
        return false;
    }
}
