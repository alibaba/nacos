/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.common.remote.TlsConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Grpc config.
 *
 * @author githubcheng2978.
 */

@ConfigurationProperties(prefix = RpcServerTlsConfig.PREFIX)
@Component
public class RpcServerTlsConfig extends TlsConfig {

    public static final  String PREFIX = "nacos.remote.server.rpc.tls";

    private Boolean compatibility = true;

    public Boolean getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(Boolean compatibility) {
        this.compatibility = compatibility;
    }
}
