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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Switch manager.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class SwitchManager implements RecordListener<SwitchDomain> {
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Resource(name = "persistentConsistencyServiceDelegate")
    private ConsistencyService consistencyService;
    
    ReentrantLock lock = new ReentrantLock();
    
    /**
     * Init switch manager.
     */
    @PostConstruct
    public void init() {
        
        try {
            consistencyService.listen(KeyBuilder.getSwitchDomainKey(), this);
        } catch (NacosException e) {
            Loggers.SRV_LOG.error("listen switch service failed.", e);
        }
    }
    
    private SwitchDomain getSwitchDomain(Datum datum) throws CloneNotSupportedException {
        if (datum != null && datum.value != null) {
            return (SwitchDomain) datum.value;
        } else {
            return this.switchDomain.clone();
        }
    }
    
    public SwitchDomain getSwitchDomain() {
        return switchDomain;
    }
    
    private void batchUpdateSwitchDomain(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        SwitchDomain dom = JacksonUtils.toObj(value, SwitchDomain.class);
        dom.setEnableStandalone(switchDomain.isEnableStandalone());
        if (dom.getHttpHealthParams().getMin() < SwitchDomain.HttpHealthParams.MIN_MIN
                || dom.getTcpHealthParams().getMin() < SwitchDomain.HttpHealthParams.MIN_MIN) {
            throw new IllegalArgumentException("min check time for http or tcp is too small(<500)");
        }
        
        if (dom.getHttpHealthParams().getMax() < SwitchDomain.HttpHealthParams.MIN_MAX
                || dom.getTcpHealthParams().getMax() < SwitchDomain.HttpHealthParams.MIN_MAX) {
            throw new IllegalArgumentException("max check time for http or tcp is too small(<3000)");
        }
        
        if (dom.getHttpHealthParams().getFactor() < 0 || dom.getHttpHealthParams().getFactor() > 1
                || dom.getTcpHealthParams().getFactor() < 0 || dom.getTcpHealthParams().getFactor() > 1) {
            throw new IllegalArgumentException("malformed factor");
        }
        
        switchDomain = dom;
    }
    
    private void updateSwitchDomain(String entry, String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        switch (entry) {
            case SwitchEntry.DISTRO_THRESHOLD:
                float threshold = Float.parseFloat(value);
                if (threshold <= 0) {
                    throw new IllegalArgumentException("distroThreshold can not be zero or negative: " + threshold);
                }
                switchDomain.setDistroThreshold(threshold);
                break;
            case SwitchEntry.CLIENT_BEAT_INTERVAL:
                switchDomain.setClientBeatInterval(Long.parseLong(value));
                break;
            case SwitchEntry.PUSH_VERSION:
                updatePushVersion(value, switchDomain);
                break;
            case SwitchEntry.PUSH_CACHE_MILLIS:
                updatePushCacheMillis(value, switchDomain);
                break;
            case SwitchEntry.DEFAULT_CACHE_MILLIS:
                updateDefaultCacheMillis(value, switchDomain);
                break;
            case SwitchEntry.MASTERS:
                switchDomain.setMasters(Arrays.asList(value.split(",")));
                break;
            case SwitchEntry.DISTRO:
                switchDomain.setDistroEnabled(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.CHECK:
                switchDomain.setHealthCheckEnabled(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.PUSH_ENABLED:
                switchDomain.setPushEnabled(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.SERVICE_STATUS_SYNC_PERIOD:
                updateServiceStatusSyncPeriod(value, switchDomain);
                break;
            case SwitchEntry.SERVER_STATUS_SYNC_PERIOD:
                updateServerStatusSyncPeriod(value, switchDomain);
                break;
            case SwitchEntry.HEALTH_CHECK_TIMES:
                switchDomain.setCheckTimes(Integer.parseInt(value));
                break;
            case SwitchEntry.DISABLE_ADD_IP:
                switchDomain.setDisableAddIp(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.SEND_BEAT_ONLY:
                switchDomain.setSendBeatOnly(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.LIMITED_URL_MAP:
                updateLimitedUrlMap(value, switchDomain);
                break;
            case SwitchEntry.ENABLE_STANDALONE:
                switchDomain.setEnableStandalone(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.OVERRIDDEN_SERVER_STATUS:
                updateOverriddenServerStatus(value, switchDomain);
                break;
            case SwitchEntry.DEFAULT_INSTANCE_EPHEMERAL:
                switchDomain.setDefaultInstanceEphemeral(Boolean.parseBoolean(value));
                break;
            case SwitchEntry.DISTRO_SERVER_EXPIRED_MILLIS:
                switchDomain.setDistroServerExpiredMillis(Long.parseLong(value));
                break;
            case SwitchEntry.LIGHT_BEAT_ENABLED:
                switchDomain.setLightBeatEnabled(ConvertUtils.toBoolean(value));
                break;
            case SwitchEntry.AUTO_CHANGE_HEALTH_CHECK_ENABLED:
                switchDomain.setAutoChangeHealthCheckEnabled(ConvertUtils.toBoolean(value));
                break;
            default:
                throw new IllegalArgumentException("unsupported entry: " + entry);
        }
    }
    
    private void updatePushVersion(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        String type = value.split(":")[0];
        String version = value.split(":")[1];
        
        if (!version.matches(UtilsAndCommons.VERSION_STRING_SYNTAX)) {
            throw new IllegalArgumentException(
                    "illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX);
        }
        
        switch (type) {
            case SwitchEntry.CLIENT_JAVA:
                switchDomain.setPushJavaVersion(version);
                break;
            case SwitchEntry.CLIENT_PYTHON:
                switchDomain.setPushPythonVersion(version);
                break;
            case SwitchEntry.CLIENT_C:
                switchDomain.setPushVersionOfC(version);
                break;
            case SwitchEntry.CLIENT_GO:
                switchDomain.setPushGoVersion(version);
                break;
            default:
                throw new IllegalArgumentException("unsupported client type: " + type);
        }
    }
    
    private void updatePushCacheMillis(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        long cacheMillis = Long.parseLong(value);
        if (cacheMillis < SwitchEntry.MIN_PUSH_CACHE_TIME_MIILIS) {
            throw new IllegalArgumentException("min cache time for http or tcp is too small(<10000)");
        }
        switchDomain.setDefaultPushCacheMillis(cacheMillis);
    }
    
    private void updateDefaultCacheMillis(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        long cacheMillis = Long.parseLong(value);
        if (cacheMillis < SwitchEntry.MIN_CACHE_TIME_MIILIS) {
            throw new IllegalArgumentException("min default cache time is too small(<1000)");
        }
        switchDomain.setDefaultCacheMillis(cacheMillis);
    }
    
    private void updateServiceStatusSyncPeriod(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        long millis = Long.parseLong(value);
        if (millis < SwitchEntry.MIN_SERVICE_SYNC_TIME_MIILIS) {
            throw new IllegalArgumentException("serviceStatusSynchronizationPeriodMillis is too small(<5000)");
        }
        switchDomain.setServiceStatusSynchronizationPeriodMillis(millis);
    }
    
    private void updateServerStatusSyncPeriod(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        long millis = Long.parseLong(value);
        if (millis < SwitchEntry.MIN_SERVER_SYNC_TIME_MIILIS) {
            throw new IllegalArgumentException("serverStatusSynchronizationPeriodMillis is too small(<15000)");
        }
        switchDomain.setServerStatusSynchronizationPeriodMillis(millis);
    }
    
    private void updateLimitedUrlMap(String value, SwitchDomain switchDomain) throws IllegalArgumentException {
        Map<String, Integer> limitedUrlMap = new HashMap<>(16);
        if (!StringUtils.isEmpty(value)) {
            String[] entries = value.split(",");
            for (String each : entries) {
                String[] parts = each.split(":");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("invalid input for limited urls");
                }
                
                String limitedUrl = parts[0];
                if (StringUtils.isEmpty(limitedUrl)) {
                    throw new IllegalArgumentException("url can not be empty, url: " + limitedUrl);
                }
                
                int statusCode = Integer.parseInt(parts[1]);
                if (statusCode <= 0) {
                    throw new IllegalArgumentException("illegal normal status code: " + statusCode);
                }
                
                limitedUrlMap.put(limitedUrl, statusCode);
            }
            switchDomain.setLimitedUrlMap(limitedUrlMap);
        }
    }
    
    private void updateOverriddenServerStatus(String value, SwitchDomain switchDomain) {
        String status = Constants.NULL_STRING.equals(value) ? StringUtils.EMPTY : value;
        switchDomain.setOverriddenServerStatus(status);
    }
    
    /**
     * Update switch information.
     *
     * @param entry item entry of switch, {@link SwitchEntry}
     * @param value switch value
     * @param debug whether debug
     * @throws Exception exception
     */
    public void update(String entry, String value, boolean debug) throws Exception {
        lock.lock();
        try {
            Datum datum = consistencyService.get(KeyBuilder.getSwitchDomainKey());
            SwitchDomain switchDomain = getSwitchDomain(datum);
            
            if (SwitchEntry.BATCH.equals(entry)) {
                batchUpdateSwitchDomain(value, switchDomain);
            } else {
                updateSwitchDomain(entry, value, switchDomain);
            }
            
            if (debug) {
                update(switchDomain);
            } else {
                consistencyService.put(KeyBuilder.getSwitchDomainKey(), switchDomain);
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Update switch information from new switch domain.
     *
     * @param newSwitchDomain new switch domain
     */
    public void update(SwitchDomain newSwitchDomain) {
        switchDomain.setMasters(newSwitchDomain.getMasters());
        switchDomain.setAdWeightMap(newSwitchDomain.getAdWeightMap());
        switchDomain.setDefaultPushCacheMillis(newSwitchDomain.getDefaultPushCacheMillis());
        switchDomain.setClientBeatInterval(newSwitchDomain.getClientBeatInterval());
        switchDomain.setDefaultCacheMillis(newSwitchDomain.getDefaultCacheMillis());
        switchDomain.setDistroThreshold(newSwitchDomain.getDistroThreshold());
        switchDomain.setHealthCheckEnabled(newSwitchDomain.isHealthCheckEnabled());
        switchDomain.setAutoChangeHealthCheckEnabled(newSwitchDomain.isAutoChangeHealthCheckEnabled());
        switchDomain.setDistroEnabled(newSwitchDomain.isDistroEnabled());
        switchDomain.setPushEnabled(newSwitchDomain.isPushEnabled());
        switchDomain.setEnableStandalone(newSwitchDomain.isEnableStandalone());
        switchDomain.setCheckTimes(newSwitchDomain.getCheckTimes());
        switchDomain.setHttpHealthParams(newSwitchDomain.getHttpHealthParams());
        switchDomain.setTcpHealthParams(newSwitchDomain.getTcpHealthParams());
        switchDomain.setMysqlHealthParams(newSwitchDomain.getMysqlHealthParams());
        switchDomain.setIncrementalList(newSwitchDomain.getIncrementalList());
        switchDomain.setServerStatusSynchronizationPeriodMillis(
                newSwitchDomain.getServerStatusSynchronizationPeriodMillis());
        switchDomain.setServiceStatusSynchronizationPeriodMillis(
                newSwitchDomain.getServiceStatusSynchronizationPeriodMillis());
        switchDomain.setDisableAddIp(newSwitchDomain.isDisableAddIp());
        switchDomain.setSendBeatOnly(newSwitchDomain.isSendBeatOnly());
        switchDomain.setLimitedUrlMap(newSwitchDomain.getLimitedUrlMap());
        switchDomain.setDistroServerExpiredMillis(newSwitchDomain.getDistroServerExpiredMillis());
        switchDomain.setPushGoVersion(newSwitchDomain.getPushGoVersion());
        switchDomain.setPushJavaVersion(newSwitchDomain.getPushJavaVersion());
        switchDomain.setPushPythonVersion(newSwitchDomain.getPushPythonVersion());
        switchDomain.setPushVersionOfC(newSwitchDomain.getPushVersionOfC());
        switchDomain.setEnableAuthentication(newSwitchDomain.isEnableAuthentication());
        switchDomain.setOverriddenServerStatus(newSwitchDomain.getOverriddenServerStatus());
        switchDomain.setDefaultInstanceEphemeral(newSwitchDomain.isDefaultInstanceEphemeral());
        switchDomain.setLightBeatEnabled(newSwitchDomain.isLightBeatEnabled());
    }
    
    @Override
    public boolean interests(String key) {
        return KeyBuilder.matchSwitchKey(key);
    }
    
    @Override
    public boolean matchUnlistenKey(String key) {
        return KeyBuilder.matchSwitchKey(key);
    }
    
    @Override
    public void onChange(String key, SwitchDomain domain) throws Exception {
        update(domain);
    }
    
    @Override
    public void onDelete(String key) throws Exception {
    
    }
}
