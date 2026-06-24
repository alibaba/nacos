/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Config-backed storage for skill subscriptions.
 *
 * @author nacos
 */
@Service
public class ConfigSkillSubscriptionStorage implements SkillSubscriptionStorage {
    
    private static final String DIGEST_ALGORITHM = "SHA-256";
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigOperationService configOperationService;
    
    private final SyncEffectService syncEffectService;
    
    public ConfigSkillSubscriptionStorage(ConfigInfoPersistService configInfoPersistService,
        ConfigOperationService configOperationService, SyncEffectService syncEffectService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configOperationService = configOperationService;
        this.syncEffectService = syncEffectService;
    }
    
    @Override
    public SkillSubscriptionDocument get(String namespaceId, String subscriber)
        throws NacosException {
        String dataId = buildDataId(subscriber);
        ConfigAllInfo config = configInfoPersistService.findConfigAllInfo(dataId,
            Constants.Skills.SKILL_SUBSCRIPTION_GROUP, namespaceId);
        if (config == null || config.getContent() == null) {
            return emptyDocument(namespaceId, subscriber);
        }
        SkillSubscriptionDocument document =
            JacksonUtils.toObj(config.getContent(), SkillSubscriptionDocument.class);
        return document == null ? emptyDocument(namespaceId, subscriber) : document;
    }
    
    @Override
    public void save(String namespaceId, String subscriber, SkillSubscriptionDocument document)
        throws NacosException {
        final long startTimeStamp = System.currentTimeMillis();
        String dataId = buildDataId(subscriber);
        document.setNamespaceId(namespaceId);
        document.setSubscriber(subscriber);
        document.setGroupId(Constants.Skills.SKILL_SUBSCRIPTION_GROUP);
        document.setDataId(dataId);
        
        ConfigForm form = new ConfigForm();
        form.setDataId(dataId);
        form.setGroup(Constants.Skills.SKILL_SUBSCRIPTION_GROUP);
        form.setNamespaceId(namespaceId);
        form.setContent(JacksonUtils.toJson(document));
        form.setSrcUser(subscriber);
        form.setType(ConfigType.JSON.getType());
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        try {
            configOperationService.publishConfig(form, requestInfo, null);
        } catch (ConfigAlreadyExistsException alreadyExists) {
            requestInfo.setUpdateForExist(Boolean.TRUE);
            configOperationService.publishConfig(form, requestInfo, null);
        }
        if (syncEffectService != null) {
            syncEffectService.toSync(form, startTimeStamp);
        }
    }
    
    /**
     * Build subscription Config dataId from subscriber identity.
     *
     * @param subscriber subscriber identity
     * @return subscription Config dataId
     */
    public static String buildDataId(String subscriber) {
        return Constants.Skills.SKILL_SUBSCRIPTION_DATA_ID_PREFIX
            + sha256Hex(subscriber == null ? "" : subscriber)
            + Constants.Skills.SKILL_SUBSCRIPTION_DATA_ID_SUFFIX;
    }
    
    private static SkillSubscriptionDocument emptyDocument(String namespaceId, String subscriber) {
        SkillSubscriptionDocument document = new SkillSubscriptionDocument();
        document.setNamespaceId(namespaceId);
        document.setSubscriber(subscriber);
        document.setGroupId(Constants.Skills.SKILL_SUBSCRIPTION_GROUP);
        document.setDataId(buildDataId(subscriber));
        return document;
    }
    
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return MD5Utils.encodeHexString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
