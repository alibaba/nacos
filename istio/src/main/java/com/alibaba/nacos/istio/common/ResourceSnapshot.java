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

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.ServiceEntryWrapper;
import com.alibaba.nacos.istio.util.IstioCrdUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author special.fy
 */
public class ResourceSnapshot {
    private static AtomicLong versionSuffix = new AtomicLong(0);

    private final List<ServiceEntryWrapper> serviceEntries;

    private boolean isCompleted;

    private String version;

    public ResourceSnapshot() {
        isCompleted = false;
        serviceEntries = new ArrayList<>();
    }

    public synchronized void initResourceSnapshot(NacosResourceManager manager) {
        if (isCompleted) {
            return;
        }

        initServiceEntry(manager);

        generateVersion();

        isCompleted = true;
    }

    private void generateVersion() {
        String time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
        version = time + "/" + versionSuffix.getAndIncrement();
    }

    private void initServiceEntry(NacosResourceManager manager) {
       Map<String, IstioService> serviceInfoMap = manager.services();
       for (String serviceName : serviceInfoMap.keySet()) {
           ServiceEntryWrapper serviceEntryWrapper = IstioCrdUtil.buildServiceEntry(serviceName, manager.getIstioConfig().getDomainSuffix(), serviceInfoMap.get(serviceName));
           if (serviceEntryWrapper != null) {
               serviceEntries.add(serviceEntryWrapper);
           }
       }

    }

    public List<ServiceEntryWrapper> getServiceEntries() {
        return serviceEntries;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public String getVersion() {
        return version;
    }
}
