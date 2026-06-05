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
