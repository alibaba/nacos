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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.config.remote.response.cluster.ConfigChangeClusterSyncResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.config.server.configuration.ConfigCompatibleConfig;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.ConfigMigrateService;
import com.alibaba.nacos.config.server.service.dump.DumpRequest;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.grpc.InvokeSource;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.stereotype.Component;

/**
 * handler to handler config change from other servers.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeClusterSyncRequestHandler.java, v 0.1 2020年08月11日 4:35 PM liuzunfei Exp $
 */
@Component
@InvokeSource(source = {RemoteConstants.LABEL_SOURCE_CLUSTER})
public class ConfigChangeClusterSyncRequestHandler
        extends RequestHandler<ConfigChangeClusterSyncRequest, ConfigChangeClusterSyncResponse> {
    
    private final DumpService dumpService;
    
    private ConfigMigrateService configMigrateService;
    
    public ConfigChangeClusterSyncRequestHandler(DumpService dumpService,
            ConfigMigrateService configMigrateService) {
        this.dumpService = dumpService;
        this.configMigrateService = configMigrateService;
    }

    @Override
    @NamespaceValidation
    @TpsControl(pointName = "ClusterConfigChangeNotify")
    @ExtractorManager.Extractor(rpcExtractor = ConfigRequestParamExtractor.class)
    @Secured(signType = SignType.CONFIG, apiType = ApiType.INNER_API)
    public ConfigChangeClusterSyncResponse handle(ConfigChangeClusterSyncRequest configChangeSyncRequest,
            RequestMeta meta) throws NacosException {
        
        checkCompatity(configChangeSyncRequest, meta);
        
        ParamUtils.checkParam(configChangeSyncRequest.getTag());
        DumpRequest dumpRequest = DumpRequest.create(configChangeSyncRequest.getDataId(),
                configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant(),
                configChangeSyncRequest.getLastModified(), meta.getClientIp());
        
        dumpRequest.setGrayName(configChangeSyncRequest.getGrayName());
        dumpService.dump(dumpRequest);
        return new ConfigChangeClusterSyncResponse();
    }
    
    /**
     * if notified from old server,try to migrate and transfer gray model.
     *
     * @param configChangeSyncRequest request.
     */
    private void checkCompatity(ConfigChangeClusterSyncRequest configChangeSyncRequest, RequestMeta meta) {
        if (PropertyUtil.isGrayCompatibleModel() && StringUtils.isBlank(configChangeSyncRequest.getGrayName())) {
            if (configChangeSyncRequest.isBeta() || StringUtils.isNotBlank(configChangeSyncRequest.getTag())) {
                
                String grayName = null;
                //from old server ,beta or tag persist into old model,try migrate and transfer gray model.
                if (configChangeSyncRequest.isBeta()) {
                    configMigrateService.checkMigrateBeta(configChangeSyncRequest.getDataId(),
                            configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant());
                    grayName = BetaGrayRule.TYPE_BETA;
                } else {
                    configMigrateService.checkMigrateTag(configChangeSyncRequest.getDataId(),
                            configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant(),
                            configChangeSyncRequest.getTag());
                    grayName = TagGrayRule.TYPE_TAG + "_" + configChangeSyncRequest.getTag();
                }
                configChangeSyncRequest.setGrayName(grayName);
                
            }
        }
        
        if (!checkNamespaceCompatible(configChangeSyncRequest, meta)) {
            return;
        }
        
        if (StringUtils.isNotBlank(configChangeSyncRequest.getGrayName())) {
            configMigrateService.namespaceMigrateGray(configChangeSyncRequest.getDataId(),
                    configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant(),
                    configChangeSyncRequest.getGrayName());
        } else {
            configMigrateService.namespaceMigrate(configChangeSyncRequest.getDataId(),
                    configChangeSyncRequest.getGroup(), configChangeSyncRequest.getTenant());
        }
        
        configChangeSyncRequest.setTenant("public");
    }
    
    /**
     * Check namespace compatible boolean.
     *
     * @param configSyncRequest the config sync request
     * @param meta              the meta
     * @return the boolean
     */
    public boolean checkNamespaceCompatible(ConfigChangeClusterSyncRequest configSyncRequest, RequestMeta meta) {
        if (!ConfigCompatibleConfig.getInstance().isNamespaceCompatibleMode()) {
            return false;
        }
        final String ignoreCheckVersion = "3.0.0";
        final String clusterVersionPrefixNew = "Nacos-Server:v";
        final String clusterVersionPrefixOld = "Nacos-Java-Client:v";
        try {
            String version = null;
            if (meta.getClientVersion().split(clusterVersionPrefixNew).length > 1) {
                version = meta.getClientVersion().split(clusterVersionPrefixNew)[1];
            } else {
                version = meta.getClientVersion().split(clusterVersionPrefixOld)[1];
            }
            if (VersionUtils.compareVersion(version, ignoreCheckVersion) >= 0) {
                return false;
            }
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST.error("checkCompatity error", e);
        }
        return StringUtils.equals(configSyncRequest.getTenant(), "public") || StringUtils.isBlank(
                configSyncRequest.getTenant());
    }
    
}
