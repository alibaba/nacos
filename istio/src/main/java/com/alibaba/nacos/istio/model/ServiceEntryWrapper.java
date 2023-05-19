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

package com.alibaba.nacos.istio.model;

import istio.mcp.v1alpha1.MetadataOuterClass.Metadata;
import istio.networking.v1alpha3.ServiceEntryOuterClass.ServiceEntry;

/**
 * @author special.fy
 */
public class ServiceEntryWrapper {

    private Metadata metadata;

    private ServiceEntry serviceEntry;

    public ServiceEntryWrapper(Metadata metadata, ServiceEntry serviceEntry) {
        this.metadata = metadata;
        this.serviceEntry = serviceEntry;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public ServiceEntry getServiceEntry() {
        return serviceEntry;
    }
}
