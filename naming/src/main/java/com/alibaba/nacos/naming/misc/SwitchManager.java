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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.cp.simpleraft.Datum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Switch manager
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @since 1.0.0
 */
@Component
public class SwitchManager {

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private ConsistencyService consistencyService;

    ReentrantLock lock = new ReentrantLock();

    public SwitchManager() {

        try {
            consistencyService.listen(UtilsAndCommons.getDomStoreKey(switchDomain), switchDomain);
        } catch (NacosException e) {
            Loggers.SRV_LOG.error("listen switch domain failed.", e);
        }
    }

    public void update(String entry, String value, boolean debug) throws NacosException {

        try {
            lock.lock();

            Datum datum = (Datum) consistencyService.get(UtilsAndCommons.getDomStoreKey(switchDomain));
            SwitchDomain switchDomain = null;

            if (datum != null) {
                switchDomain = JSON.parseObject(datum.value, SwitchDomain.class);
            } else {
                Loggers.SRV_LOG.warn("switch domain is null");
                throw new NacosException(NacosException.SERVER_ERROR, "switch datum is null!");
            }

            if (SwitchEntry.BATCH.equals(entry)) {
                //batch update
                SwitchDomain dom = JSON.parseObject(value, SwitchDomain.class);
                dom.setEnableStandalone(switchDomain.isEnableStandalone());
                if (dom.httpHealthParams.getMin() < SwitchDomain.HttpHealthParams.MIN_MIN
                    || dom.tcpHealthParams.getMin() < SwitchDomain.HttpHealthParams.MIN_MIN) {

                    throw new IllegalArgumentException("min check time for http or tcp is too small(<500)");
                }

                if (dom.httpHealthParams.getMax() < SwitchDomain.HttpHealthParams.MIN_MAX
                    || dom.tcpHealthParams.getMax() < SwitchDomain.HttpHealthParams.MIN_MAX) {

                    throw new IllegalArgumentException("max check time for http or tcp is too small(<3000)");
                }

                if (dom.httpHealthParams.getFactor() < 0
                    || dom.httpHealthParams.getFactor() > 1
                    || dom.tcpHealthParams.getFactor() < 0
                    || dom.tcpHealthParams.getFactor() > 1) {

                    throw new IllegalArgumentException("malformed factor");
                }

                switchDomain.replace(dom);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(dom), JSON.toJSONString(dom));
                }

                return;
            }

            if (entry.equals(SwitchEntry.DISTRO_THRESHOLD)) {
                Float threshold = Float.parseFloat(value);

                if (threshold <= 0) {
                    throw new IllegalArgumentException("distroThreshold can not be zero or negative: " + threshold);
                }


                switchDomain.setDistroThreshold(threshold);

                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                }
                return;
            }


            if (entry.equals(SwitchEntry.ENABLE_ALL_DOM_NAME_CACHE)) {
                Boolean enable = Boolean.parseBoolean(value);
                switchDomain.setAllDomNameCache(enable);

                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                }

                return;
            }

            if (entry.equals(SwitchEntry.CLIENT_BEAT_INTERVAL)) {
                long clientBeatInterval = Long.parseLong(value);
                switchDomain.setClientBeatInterval(clientBeatInterval);

                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
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

                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.PUSH_CACHE_MILLIS)) {
                Long cacheMillis = Long.parseLong(value);

                if (cacheMillis < SwitchEntry.MIN_PUSH_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min cache time for http or tcp is too small(<10000)");
                }

                switchDomain.setPushCacheMillis(cacheMillis);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            // extremely careful while modifying this, cause it will affect all clients without pushing enabled
            if (entry.equals(SwitchEntry.DEFAULT_CACHE_MILLIS)) {
                Long cacheMillis = Long.parseLong(value);

                if (cacheMillis < SwitchEntry.MIN_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min default cache time  is too small(<1000)");
                }

                switchDomain.setDefaultCacheMillis(cacheMillis);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.MASTERS)) {
                List<String> masters = Arrays.asList(value.split(","));

                switchDomain.setMasters(masters);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                }
                return;
            }

            if (entry.equals(SwitchEntry.DISTRO)) {
                boolean enabled = Boolean.parseBoolean(value);

                switchDomain.setDistroEnabled(enabled);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.CHECK)) {
                boolean enabled = Boolean.parseBoolean(value);

                switchDomain.setHealthCheckEnabled(enabled);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.DEFAULT_HEALTH_CHECK_MODE)) {
                String defaultHealthCheckMode = value;

                switchDomain.setDefaultHealthCheckMode(defaultHealthCheckMode);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.DOM_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(value);

                if (millis < SwitchEntry.MIN_DOM_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("domStatusSynchronizationPeriodMillis is too small(<5000)");
                }

                switchDomain.setDomStatusSynchronizationPeriodMillis(millis);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.SERVER_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(value);

                if (millis < SwitchEntry.MIN_SERVER_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("serverStatusSynchronizationPeriodMillis is too small(<15000)");
                }

                switchDomain.setServerStatusSynchronizationPeriodMillis(millis);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.HEALTH_CHECK_TIMES)) {
                Integer times = Integer.parseInt(value);

                switchDomain.setCheckTimes(times);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.DISABLE_ADD_IP)) {
                boolean disableAddIP = Boolean.parseBoolean(value);

                switchDomain.setDisableAddIP(disableAddIP);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.ENABLE_CACHE)) {
                boolean enableCache = Boolean.parseBoolean(value);

                switchDomain.setEnableCache(enableCache);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
            }

            if (entry.equals(SwitchEntry.SEND_BEAT_ONLY)) {
                boolean sendBeatOnly = Boolean.parseBoolean(value);

                switchDomain.setSendBeatOnly(sendBeatOnly);
                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }
                return;
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
                    if (!debug) {
                        consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                        ;
                    }
                    return;
                }
            }

            if (entry.equals(SwitchEntry.ENABLE_STANDALONE)) {
                String enable = value;

                if (!StringUtils.isNotEmpty(enable)) {
                    switchDomain.setEnableStandalone(Boolean.parseBoolean(enable));
                }

                if (!debug) {
                    consistencyService.put(UtilsAndCommons.getDomStoreKey(switchDomain), JSON.toJSONString(switchDomain));
                    ;
                }

                return;
            }

            throw new IllegalArgumentException("update entry not found: " + entry);
        } finally {
            lock.unlock();
        }

    }

    public void update(SwitchDomain newSwitchDomain) {

    }
}
