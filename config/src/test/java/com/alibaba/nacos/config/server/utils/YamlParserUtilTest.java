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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.model.ConfigMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlParserUtilTest {
    
    private static final String CONFIG_METADATA_STRING =
            "metadata:\n" + "- dataId: testData1\n" + "  group: testGroup1\n" + "  type: text\n" + "- appName: testAppName\n"
                    + "  dataId: testData2\n" + "  desc: test desc\n" + "  group: testGroup2\n" + "  type: yaml\n";
    
    private ConfigMetadata.ConfigExportItem item1;
    
    private ConfigMetadata.ConfigExportItem item2;
    
    @BeforeEach
    void setUp() {
        item1 = new ConfigMetadata.ConfigExportItem();
        item1.setDataId("testData1");
        item1.setGroup("testGroup1");
        item1.setType("text");
        
        item2 = new ConfigMetadata.ConfigExportItem();
        item2.setDataId("testData2");
        item2.setGroup("testGroup2");
        item2.setType("yaml");
        item2.setAppName("testAppName");
        item2.setDesc("test desc");
    }
    
    @Test
    void testDumpObject() {
        ConfigMetadata configMetadata = new ConfigMetadata();
        List<ConfigMetadata.ConfigExportItem> configMetadataItems = new ArrayList<>();
        configMetadataItems.add(item1);
        configMetadataItems.add(item2);
        configMetadata.setMetadata(configMetadataItems);
        
        String parseString = YamlParserUtil.dumpObject(configMetadata);
        assertEquals(CONFIG_METADATA_STRING, parseString);
    }
    
    @Test
    void testLoadObject() {
        ConfigMetadata configMetadata = YamlParserUtil.loadObject(CONFIG_METADATA_STRING, ConfigMetadata.class);
        assertNotNull(configMetadata);
        
        List<ConfigMetadata.ConfigExportItem> metadataList = configMetadata.getMetadata();
        assertNotNull(metadataList);
        assertEquals(2, metadataList.size());
        ConfigMetadata.ConfigExportItem configExportItem1 = metadataList.get(0);
        ConfigMetadata.ConfigExportItem configExportItem2 = metadataList.get(1);
        assertEquals(configExportItem1, item1);
        assertEquals(configExportItem2, item2);
    }
    
    @Test
    void testNotSupportType() {
        assertThrows(ConstructorException.class, () -> {
            YamlParserUtil.loadObject("name: test", YamlTest.class);
        });
    }
    
    private static class YamlTest {
        
        private String name;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
    }
}
