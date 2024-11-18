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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.springframework.stereotype.Component;

/**
 * request handler to publish config.
 *
 * @author liuzunfei
 * @version $Id: ConfigPublishRequestHandler.java, v 0.1 2020年07月16日 4:41 PM liuzunfei Exp $
 */
@Component
public class ConfigPublishRequestHandler extends RequestHandler<ConfigPublishRequest, ConfigPublishResponse> {
    
    private ConfigOperationService configOperationService;
    
    public ConfigPublishRequestHandler(ConfigOperationService configOperationService) {
        this.configOperationService = configOperationService;
    }
    
    @Override
    @TpsControl(pointName = "ConfigPublish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(rpcExtractor = ConfigRequestParamExtractor.class)
    public ConfigPublishResponse handle(ConfigPublishRequest request, RequestMeta meta) throws NacosException {
        
        try {
            String dataId = request.getDataId();
            String group = request.getGroup();
            String content = request.getContent();
            final String tenant = request.getTenant();
            
            final String srcIp = meta.getClientIp();
            final String tag = request.getAdditionParam("tag");
            final String appName = request.getAdditionParam("appName");
            final String type = request.getAdditionParam("type");
            final String srcUser = request.getAdditionParam("src_user");
            final String encryptedDataKey = request.getAdditionParam("encryptedDataKey");
            
            // check tenant
            ParamUtils.checkParam(dataId, group, "datumId", content);
            ParamUtils.checkParam(tag);
            
            ConfigForm configForm = new ConfigForm();
            configForm.setDataId(dataId);
            configForm.setGroup(group);
            configForm.setNamespaceId(tenant);
            configForm.setContent(content);
            configForm.setTag(tag);
            configForm.setAppName(appName);
            configForm.setSrcUser(srcUser);
            configForm.setConfigTags(request.getAdditionParam("config_tags"));
            configForm.setDesc(request.getAdditionParam("desc"));
            configForm.setUse(request.getAdditionParam("use"));
            configForm.setEffect(request.getAdditionParam("effect"));
            configForm.setType(type);
            configForm.setSchema(request.getAdditionParam("schema"));
            
            if (!ConfigType.isValidType(type)) {
                configForm.setType(ConfigType.getDefaultType().getType());
            }
            
            ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
            configRequestInfo.setSrcIp(srcIp);
            configRequestInfo.setRequestIpApp(meta.getLabels().get(Constants.APPNAME));
            configRequestInfo.setBetaIps(request.getAdditionParam("betaIps"));
            configRequestInfo.setCasMd5(request.getCasMd5());
            
            String encryptedDataKeyFinal = null;
            if (StringUtils.isNotBlank(encryptedDataKey)) {
                encryptedDataKeyFinal = encryptedDataKey;
            } else {
                Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
                content = pair.getSecond();
                encryptedDataKeyFinal = pair.getFirst();
                configForm.setContent(content);
            }
            try {
                configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal);
                return ConfigPublishResponse.buildSuccessResponse();
            } catch (NacosApiException nacosApiException) {
                return ConfigPublishResponse.buildFailResponse(ResponseCode.FAIL.getCode(),
                        nacosApiException.getErrMsg());
            }
            
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST.error("[ConfigPublishRequestHandler] publish config error ,request ={}", request, e);
            return ConfigPublishResponse.buildFailResponse(
                    (e instanceof NacosException) ? ((NacosException) e).getErrCode() : ResponseCode.FAIL.getCode(),
                    e.getMessage());
        }
    }
    
}
