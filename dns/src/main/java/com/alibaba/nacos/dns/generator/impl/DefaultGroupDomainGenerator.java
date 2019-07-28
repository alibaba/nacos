/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.nacos.dns.generator.impl;

import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.dns.generator.DomainGenerator;
import com.alibaba.nacos.naming.core.Service;
import org.springframework.stereotype.Component;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;

/**
 * @author paderlol
 */
@Component
public class DefaultGroupDomainGenerator implements DomainGenerator {

    private static final String FULL_DOMAIN_MAPPING = "%s.sn.%s.ns" + DOMAIN_SUFFIX;

    @Override
    public boolean isMatch(Service service) {

        return DEFAULT_GROUP.equals(NamingUtils.getGroupName(service.getName()));
    }

    @Override
    public String create(Service service) {

        return String.format(FULL_DOMAIN_MAPPING, NamingUtils.getServiceName(service.getName()),
            service.getNamespaceId());
    }
}
