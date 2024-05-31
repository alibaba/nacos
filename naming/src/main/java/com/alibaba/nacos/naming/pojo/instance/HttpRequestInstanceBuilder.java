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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * Http instance builder.
 *
 * <p>
 * The http openAPI will split each attributes of {@link Instance} as parameters of http parameters. This Builder can
 * set an http request and get necessary parameters to build {@link Instance}.
 * </p>
 *
 * <p>
 * This builder is a wrapper for {@link com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder} and will inject some
 * extension handler by spi.
 * </p>
 *
 * @author xiweng.yy
 */
public class HttpRequestInstanceBuilder {
    
    private final InstanceBuilder actualBuilder;
    
    private final Collection<InstanceExtensionHandler> handlers;
    
    private boolean defaultInstanceEphemeral = true;
    
    private HttpRequestInstanceBuilder() {
        this.actualBuilder = InstanceBuilder.newBuilder();
        this.handlers = NacosServiceLoader.newServiceInstances(InstanceExtensionHandler.class);
    }
    
    public static HttpRequestInstanceBuilder newBuilder() {
        return new HttpRequestInstanceBuilder();
    }
    
    /**
     * Build a new {@link Instance} and chain handled by {@link InstanceExtensionHandler}.
     *
     * @return new instance
     */
    public Instance build() {
        Instance result = actualBuilder.build();
        for (InstanceExtensionHandler each : handlers) {
            each.handleExtensionInfo(result);
        }
        setInstanceId(result);
        return result;
    }
    
    public HttpRequestInstanceBuilder setDefaultInstanceEphemeral(boolean defaultInstanceEphemeral) {
        this.defaultInstanceEphemeral = defaultInstanceEphemeral;
        return this;
    }
    
    public HttpRequestInstanceBuilder setRequest(HttpServletRequest request) throws NacosException {
        for (InstanceExtensionHandler each : handlers) {
            each.configExtensionInfoFromRequest(request);
        }
        setAttributesToBuilder(request);
        return this;
    }
    
    private void setAttributesToBuilder(HttpServletRequest request) throws NacosException {
        actualBuilder.setServiceName(WebUtils.required(request, CommonParams.SERVICE_NAME));
        actualBuilder.setIp(WebUtils.required(request, "ip"));
        actualBuilder.setPort(Integer.parseInt(WebUtils.required(request, "port")));
        actualBuilder.setHealthy(ConvertUtils.toBoolean(WebUtils.optional(request, "healthy", "true")));
        actualBuilder.setEphemeral(ConvertUtils
                .toBoolean(WebUtils.optional(request, "ephemeral", String.valueOf(defaultInstanceEphemeral))));
        setWeight(request);
        setCluster(request);
        setEnabled(request);
        setMetadata(request);
    }
    
    private void setWeight(HttpServletRequest request) throws NacosException {
        double weight = Double.parseDouble(WebUtils.optional(request, "weight", "1"));
        if (weight > Constants.MAX_WEIGHT_VALUE || weight < Constants.MIN_WEIGHT_VALUE) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "instance format invalid: The weights range from " + Constants.MIN_WEIGHT_VALUE + " to "
                            + Constants.MAX_WEIGHT_VALUE);
        }
        actualBuilder.setWeight(weight);
    }
    
    private void setCluster(HttpServletRequest request) {
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, StringUtils.EMPTY);
        if (StringUtils.isBlank(cluster)) {
            cluster = WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }
        actualBuilder.setClusterName(cluster);
    }
    
    private void setEnabled(HttpServletRequest request) {
        String enabledString = WebUtils.optional(request, "enabled", StringUtils.EMPTY);
        boolean enabled;
        if (StringUtils.isBlank(enabledString)) {
            enabled = ConvertUtils.toBoolean(WebUtils.optional(request, "enable", "true"));
        } else {
            enabled = ConvertUtils.toBoolean(enabledString);
        }
        actualBuilder.setEnabled(enabled);
    }
    
    private void setMetadata(HttpServletRequest request) throws NacosException {
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        if (StringUtils.isNotEmpty(metadata)) {
            actualBuilder.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }
    }
    
    private void setInstanceId(Instance instance) {
        instance.setInstanceId(InstanceIdGeneratorManager.generateInstanceId(instance));
    }
}
