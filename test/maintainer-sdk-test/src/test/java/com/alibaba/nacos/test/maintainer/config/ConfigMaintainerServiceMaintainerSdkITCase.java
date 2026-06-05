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

package com.alibaba.nacos.test.maintainer.config;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ConfigMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: maintainer SDK can publish, query, list, search,
 *     update metadata, and delete configuration through the admin API.</li>
 *     <li>Boundary/validation: missing config and invalid required publish
 *     parameters fail with controlled SDK exceptions.</li>
 *     <li>Error handling: batch delete by storage ID succeeds and cleanup
 *     tolerates already deleted resources.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
class ConfigMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    @Test
    void shouldManageConfigLifecycle() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String dataId = randomDataId("config-lifecycle");
        String group = randomGroup("config");
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String content = "maintainer.config.first=true";
        String updatedContent = "maintainer.config.second=true";
        String appName = "maintainer-sdk-it";
        String configTags = "maintainer,it";
        String desc = "maintainer sdk integration test config";
        String updatedDesc = "updated maintainer sdk integration test config";
        String updatedTags = "maintainer,updated";
        addCleanup(() -> maintainerService.deleteConfig(dataId, group, namespaceId));
        
        assertThrows(NacosException.class,
                () -> maintainerService.getConfig(dataId, group, namespaceId));
        assertTrue(maintainerService.publishConfig(dataId, group, namespaceId, content, appName,
                "maintainer", configTags, desc, ConfigType.YAML.getType()));
        
        ConfigDetailInfo detail = maintainerService.getConfig(dataId, group, namespaceId);
        assertConfigDetail(detail, dataId, group, namespaceId, content);
        assertEquals(ConfigType.YAML.getType(), detail.getType());
        assertEquals(appName, detail.getAppName());
        assertEquals(desc, detail.getDesc());
        assertEquals(configTags, detail.getConfigTags());
        assertNotNull(detail.getId());
        
        Page<ConfigBasicInfo> exactPage =
                maintainerService.listConfigs(dataId, group, namespaceId, ConfigType.YAML.getType());
        assertContainsConfig(exactPage, dataId, group);
        Page<ConfigBasicInfo> blurPage =
                maintainerService.searchConfigs(dataId.substring(0, dataId.length() - 5), group,
                        namespaceId, "first", ConfigType.YAML.getType());
        assertContainsConfig(blurPage, dataId, group);
        List<ConfigBasicInfo> configsByNamespace =
                maintainerService.getConfigListByNamespace(namespaceId);
        assertTrue(configsByNamespace.stream()
                .anyMatch(config -> dataId.equals(config.getDataId())
                        && group.equals(config.getGroupName())));
        
        assertTrue(maintainerService.updateConfigMetadata(dataId, group, namespaceId, updatedDesc,
                updatedTags));
        ConfigDetailInfo metadataUpdated = maintainerService.getConfig(dataId, group, namespaceId);
        assertEquals(updatedDesc, metadataUpdated.getDesc());
        assertEquals(updatedTags, metadataUpdated.getConfigTags());
        
        assertTrue(maintainerService.publishConfig(dataId, group, namespaceId, updatedContent));
        ConfigDetailInfo updated = maintainerService.getConfig(dataId, group, namespaceId);
        assertConfigDetail(updated, dataId, group, namespaceId, updatedContent);
        
        assertTrue(maintainerService.deleteConfig(dataId, group, namespaceId));
        assertThrows(NacosException.class,
                () -> maintainerService.getConfig(dataId, group, namespaceId));
    }
    
    @Test
    void shouldDeleteConfigByStorageId() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String dataId = randomDataId("batch-delete");
        String group = randomGroup("config");
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        addCleanup(() -> maintainerService.deleteConfig(dataId, group, namespaceId));
        
        assertTrue(maintainerService.publishConfig(dataId, group, namespaceId,
                "maintainer.config.batch.delete=true"));
        ConfigDetailInfo detail = maintainerService.getConfig(dataId, group, namespaceId);
        assertNotNull(detail.getId());
        
        assertTrue(maintainerService.deleteConfigs(Collections.singletonList(detail.getId())));
        assertThrows(NacosException.class,
                () -> maintainerService.getConfig(dataId, group, namespaceId));
    }
    
    @Test
    void shouldQueryConfigHistory() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String dataId = randomDataId("history");
        String group = randomGroup("config");
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String firstContent = "maintainer.config.history.first=true";
        String secondContent = "maintainer.config.history.second=true";
        addCleanup(() -> maintainerService.deleteConfig(dataId, group, namespaceId));
        
        assertTrue(maintainerService.publishConfig(dataId, group, namespaceId, firstContent));
        assertTrue(maintainerService.publishConfig(dataId, group, namespaceId, secondContent));
        ConfigDetailInfo currentConfig = maintainerService.getConfig(dataId, group, namespaceId);
        assertConfigDetail(currentConfig, dataId, group, namespaceId, secondContent);
        assertNotNull(currentConfig.getId());
        
        Page<ConfigHistoryBasicInfo> historyPage =
                maintainerService.listConfigHistory(dataId, group, namespaceId, 1, 10);
        assertNotNull(historyPage);
        assertTrue(historyPage.getTotalCount() >= 2,
                "two publishes should produce at least two history rows");
        
        ConfigHistoryBasicInfo newestHistory = historyPage.getPageItems().get(0);
        assertEquals(dataId, newestHistory.getDataId());
        assertEquals(group, newestHistory.getGroupName());
        assertEquals(namespaceId, newestHistory.getNamespaceId());
        assertNotNull(newestHistory.getId());

        boolean hasInitialHistory = false;
        for (ConfigHistoryBasicInfo history : historyPage.getPageItems()) {
            assertEquals(dataId, history.getDataId());
            assertEquals(group, history.getGroupName());
            assertEquals(namespaceId, history.getNamespaceId());
            assertNotNull(history.getId());
            if (history.getOpType().trim().startsWith("I")) {
                hasInitialHistory = true;
            }
        }
        assertTrue(hasInitialHistory, "history list should contain the initial publish record");

        ConfigHistoryDetailInfo historyDetail =
                maintainerService.getConfigHistoryInfo(dataId, group, namespaceId,
                        newestHistory.getId());
        assertEquals(firstContent, historyDetail.getContent());
        
        ConfigHistoryDetailInfo previousDetail =
                maintainerService.getPreviousConfigHistoryInfo(dataId, group, namespaceId,
                        currentConfig.getId());
        assertEquals(firstContent, previousDetail.getContent());
        assertEquals(newestHistory.getId(), previousDetail.getId());
    }
    
    @Test
    void shouldManageBetaConfig() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String dataId = randomDataId("beta");
        String group = randomGroup("config");
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String content = "maintainer.config.beta=true";
        String betaIps = "127.0.0.1";
        addCleanup(() -> maintainerService.stopBeta(dataId, group, namespaceId));
        addCleanup(() -> maintainerService.deleteConfig(dataId, group, namespaceId));
        
        NacosException missingBetaIps = assertThrows(NacosException.class,
                () -> maintainerService.publishBetaConfig(dataId, group, namespaceId, content,
                        null, null, null, null, ConfigType.TEXT.getType(), ""));
        assertEquals(NacosException.INVALID_PARAM, missingBetaIps.getErrCode());
        
        assertTrue(maintainerService.publishBetaConfig(dataId, group, namespaceId, content,
                "maintainer-sdk-it", "maintainer", "beta", "beta config",
                ConfigType.TEXT.getType(), betaIps));
        ConfigGrayInfo beta = maintainerService.queryBeta(dataId, group, namespaceId);
        assertConfigDetail(beta, dataId, group, namespaceId, content);
        assertEquals("beta", beta.getGrayName());
        assertNotNull(beta.getGrayRule());
        
        assertTrue(maintainerService.stopBeta(dataId, group, namespaceId));
        assertThrows(NacosException.class, () -> maintainerService.queryBeta(dataId, group,
                namespaceId));
    }
    
    @Test
    void shouldQueryConfigListenerDiagnostics() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String dataId = randomDataId("listener");
        String group = randomGroup("config");
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        addCleanup(() -> maintainerService.deleteConfig(dataId, group, namespaceId));
        
        assertTrue(maintainerService.publishConfig(dataId, group, namespaceId,
                "maintainer.config.listener=true"));
        ConfigListenerInfo configListeners =
                maintainerService.getListeners(dataId, group, namespaceId, false);
        assertNotNull(configListeners);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_CONFIG, configListeners.getQueryType());
        assertNotNull(configListeners.getListenersStatus());
        
        ConfigListenerInfo ipListeners =
                maintainerService.getAllSubClientConfigByIp("127.0.0.1", true, namespaceId,
                        false);
        assertNotNull(ipListeners);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP, ipListeners.getQueryType());
        assertNotNull(ipListeners.getListenersStatus());
    }
    
    @Test
    void shouldRunConfigOpsMaintenanceCommands() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        
        assertEquals("Local cache updated from store successfully!",
                maintainerService.updateLocalCacheFromStore());
        assertEquals("Log level updated successfully! Module: config-server, Log Level: INFO",
                maintainerService.setLogLevel("config-server", "INFO"));
    }
    
    @Test
    void shouldRejectInvalidConfigParameters() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        String group = randomGroup("config");
        
        assertThrows(NacosException.class,
                () -> maintainerService.publishConfig("", group, Constants.DEFAULT_NAMESPACE_ID,
                        "invalid-data-id"));
        assertThrows(NacosException.class,
                () -> maintainerService.publishConfig(randomDataId("invalid-group"), "",
                        Constants.DEFAULT_NAMESPACE_ID, "invalid-group"));
        assertThrows(NacosException.class,
                () -> maintainerService.publishConfig(randomDataId("invalid-content"), group,
                        Constants.DEFAULT_NAMESPACE_ID, ""));
    }
    
    private void assertConfigDetail(ConfigDetailInfo detail, String dataId, String group,
            String namespaceId, String content) {
        assertNotNull(detail);
        assertEquals(dataId, detail.getDataId());
        assertEquals(group, detail.getGroupName());
        assertEquals(namespaceId, detail.getNamespaceId());
        assertEquals(content, detail.getContent());
    }
    
    private void assertContainsConfig(Page<ConfigBasicInfo> page, String dataId, String group) {
        assertNotNull(page);
        assertTrue(page.getTotalCount() > 0, "config page should contain at least one item");
        assertTrue(page.getPageItems().stream()
                .anyMatch(config -> dataId.equals(config.getDataId())
                        && group.equals(config.getGroupName())));
    }
}
