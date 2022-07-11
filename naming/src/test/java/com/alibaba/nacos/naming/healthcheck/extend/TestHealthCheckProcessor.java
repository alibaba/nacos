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

package com.alibaba.nacos.naming.healthcheck.extend;

import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.healthcheck.v2.processor.HealthCheckProcessorV2;

/**
 * Health Check Processor For Test.
 *
 * @author sunmengying
 */
public class TestHealthCheckProcessor implements HealthCheckProcessorV2 {

    public static final String TYPE = TestChecker.TYPE;

    @Override
    public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {

    }

    @Override
    public String getType() {
        return TYPE;
    }
}