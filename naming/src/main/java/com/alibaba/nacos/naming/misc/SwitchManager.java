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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Switch manager
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class SwitchManager implements RecordListener<SwitchDomain> {

    @Autowired
    private SwitchDomain switchDomain;

    @Resource(name = "consistencyDelegate")
    private ConsistencyService consistencyService;

    ReentrantLock lock = new ReentrantLock();

    @PostConstruct
    public void init() {

        try {
            consistencyService.listen(UtilsAndCommons.getSwitchDomainKey(), this);
        } catch (NacosException e) {
            Loggers.SRV_LOG.error("listen switch service failed.", e);
        }
    }

    public void update(String entry, String value, boolean debug) throws Exception {

        try {
            lock.lock();

            Datum datum = consistencyService.get(UtilsAndCommons.getSwitchDomainKey());
            SwitchDomain switchDomain;

            if (datum != null && datum.value != null) {
                switchDomain = (SwitchDomain) datum.value;
            } else {
                switchDomain = this.switchDomain.clone();
            }

            if (SwitchEntry.BATCH.equals(entry)) {
                //batch update
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

                if (dom.getHttpHealthParams().getFactor() < 0
                    || dom.getHttpHealthParams().getFactor() > 1
                    || dom.getTcpHealthParams().getFactor() < 0
                    || dom.getTcpHealthParams().getFactor() > 1) {

                    throw new IllegalArgumentException("malformed factor");
                }

                switchDomain = dom;
            }

            if (entry.equals(SwitchEntry.DISTRO_THRESHOLD)) {
                Float threshold = Float.parseFloat(value);
                if (threshold <= 0) {
                    throw new IllegalArgumentException("distroThreshold can not be zero or negative: " + threshold);
                }
                switchDomain.setDistroThreshold(threshold);
            }

            if (entry.equals(SwitchEntry.CLIENT_BEAT_INTERVAL)) {
                long clientBeatInterval = Long.parseLong(value);
                switchDomain.setClientBeatInterval(clientBeatInterval);
            }

            if (entry.equals(SwitchEntry.PUSH_VERSION)) {

                String type = value.split(":")[0];
                String version = value.split(":")[1];

                if (!version.matches(UtilsAndCommons.VERSION_STRING_SYNTAX)) {
                    throw new IllegalArgumentException("illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX);
                }

                if (StringUtils.equals(SwitchEntry.CLIENT_JAVA, type)) {
                    switchDomain.setPushJavaVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_PYTHON, type)) {
                    switchDomain.setPushPythonVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_C, type)) {
                    switchDomain.setPushCVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_GO, type)) {
                    switchDomain.setPushGoVersion(version);
                } else {
                    throw new IllegalArgumentException("unsupported client type: " + type);
                }
            }

            if (entry.equals(SwitchEntry.PUSH_CACHE_MILLIS)) {
                Long cacheMillis = Long.parseLong(value);

                if (cacheMillis < SwitchEntry.MIN_PUSH_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min cache time for http or tcp is too small(<10000)");
                }

                switchDomain.setDefaultPushCacheMillis(cacheMillis);
            }

            // extremely careful while modifying this, cause it will affect all clients without pushing enabled
            if (entry.equals(SwitchEntry.DEFAULT_CACHE_MILLIS)) {
                Long cacheMillis = Long.parseLong(value);

                if (cacheMillis < SwitchEntry.MIN_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min default cache time  is too small(<1000)");
                }

                switchDomain.setDefaultCacheMillis(cacheMillis);
            }

            if (entry.equals(SwitchEntry.MASTERS)) {
                List<String> masters = Arrays.asList(value.split(","));
                switchDomain.setMasters(masters);
            }

            if (entry.equals(SwitchEntry.DISTRO)) {
                boolean enabled = Boolean.parseBoolean(value);
                switchDomain.setDistroEnabled(enabled);
            }

            if (entry.equals(SwitchEntry.CHECK)) {
                boolean enabled = Boolean.parseBoolean(value);
                switchDomain.setHealthCheckEnabled(enabled);
            }

            if (entry.equals(SwitchEntry.PUSH_ENABLED)) {
                boolean enabled = Boolean.parseBoolean(value);
                switchDomain.setPushEnabled(enabled);
            }

            if (entry.equals(SwitchEntry.SERVICE_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(value);

                if (millis < SwitchEntry.MIN_SERVICE_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("serviceStatusSynchronizationPeriodMillis is too small(<5000)");
                }

                switchDomain.setServiceStatusSynchronizationPeriodMillis(millis);
            }

            if (entry.equals(SwitchEntry.SERVER_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(value);

                if (millis < SwitchEntry.MIN_SERVER_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("serverStatusSynchronizationPeriodMillis is too small(<15000)");
                }

                switchDomain.setServerStatusSynchronizationPeriodMillis(millis);
            }

            if (entry.equals(SwitchEntry.HEALTH_CHECK_TIMES)) {
                Integer times = Integer.parseInt(value);

                switchDomain.setCheckTimes(times);
            }

            if (entry.equals(SwitchEntry.DISABLE_ADD_IP)) {
                boolean disableAddIP = Boolean.parseBoolean(value);

                switchDomain.setDisableAddIP(disableAddIP);
            }

            if (entry.equals(SwitchEntry.SEND_BEAT_ONLY)) {
                boolean sendBeatOnly = Boolean.parseBoolean(value);

                switchDomain.setSendBeatOnly(sendBeatOnly);
            }

            if (entry.equals(SwitchEntry.LIMITED_URL_MAP)) {
                Map<String, Integer> limitedUrlMap = new HashMap<>(16);
                String limitedUrls = value;

                if (!StringUtils.isEmpty(limitedUrls)) {
                    String[] entries = limitedUrls.split(",");
                    for (int i = 0; i < entries.length; i++) {
                        String[] parts = entries[i].split(":");
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

            if (entry.equals(SwitchEntry.ENABLE_STANDALONE)) {
                String enabled = value;

                if (!StringUtils.isNotEmpty(enabled)) {
                    switchDomain.setEnableStandalone(Boolean.parseBoolean(enabled));
                }
            }

            if (entry.equals(SwitchEntry.OVERRIDDEN_SERVER_STATUS)) {
                String status = value;
                if (Constants.NULL_STRING.equals(status)) {
                    status = StringUtils.EMPTY;
                }
                switchDomain.setOverriddenServerStatus(status);
            }

            if (entry.equals(SwitchEntry.DEFAULT_INSTANCE_EPHEMERAL)) {
                String defaultEphemeral = value;
                switchDomain.setDefaultInstanceEphemeral(Boolean.parseBoolean(defaultEphemeral));
            }

            if (entry.equals(SwitchEntry.DISTRO_SERVER_EXPIRED_MILLIS)) {
                String distroServerExpiredMillis = value;
                switchDomain.setDistroServerExpiredMillis(Long.parseLong(distroServerExpiredMillis));
            }

            if (entry.equals(SwitchEntry.LIGHT_BEAT_ENABLED)) {
                String lightBeatEnabled = value;
                switchDomain.setLightBeatEnabled(BooleanUtils.toBoolean(lightBeatEnabled));
            }

            if (entry.equals(SwitchEntry.AUTO_CHANGE_HEALTH_CHECK_ENABLED)) {
                String autoChangeHealthCheckEnabled = value;
                switchDomain.setAutoChangeHealthCheckEnabled(BooleanUtils.toBoolean(autoChangeHealthCheckEnabled));
            }

            if (debug) {
                update(switchDomain);
            } else {
                consistencyService.put(UtilsAndCommons.getSwitchDomainKey(), switchDomain);
            }

        } finally {
            lock.unlock();
        }

    }

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
        switchDomain.setServerStatusSynchronizationPeriodMillis(newSwitchDomain.getServerStatusSynchronizationPeriodMillis());
        switchDomain.setServiceStatusSynchronizationPeriodMillis(newSwitchDomain.getServiceStatusSynchronizationPeriodMillis());
        switchDomain.setDisableAddIP(newSwitchDomain.isDisableAddIP());
        switchDomain.setSendBeatOnly(newSwitchDomain.isSendBeatOnly());
        switchDomain.setLimitedUrlMap(newSwitchDomain.getLimitedUrlMap());
        switchDomain.setDistroServerExpiredMillis(newSwitchDomain.getDistroServerExpiredMillis());
        switchDomain.setPushGoVersion(newSwitchDomain.getPushGoVersion());
        switchDomain.setPushJavaVersion(newSwitchDomain.getPushJavaVersion());
        switchDomain.setPushPythonVersion(newSwitchDomain.getPushPythonVersion());
        switchDomain.setPushCVersion(newSwitchDomain.getPushCVersion());
        switchDomain.setEnableAuthentication(newSwitchDomain.isEnableAuthentication());
        switchDomain.setOverriddenServerStatus(newSwitchDomain.getOverriddenServerStatus());
        switchDomain.setDefaultInstanceEphemeral(newSwitchDomain.isDefaultInstanceEphemeral());
        switchDomain.setLightBeatEnabled(newSwitchDomain.isLightBeatEnabled());
    }

    public SwitchDomain getSwitchDomain() {
        return switchDomain;
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
