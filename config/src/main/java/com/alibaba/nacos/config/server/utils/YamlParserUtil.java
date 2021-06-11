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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * YamlParserUtil.
 *
 * @author Nacos
 */
public class YamlParserUtil {
    
    private static Yaml yaml;
    
    static {
        Representer representer = new Representer() {
            
            @Override
            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue,
                    Tag customTag) {
                if (propertyValue == null) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        yaml = new Yaml(new YamlParserConstructor(), representer);
    }
    
    /**
     * Serialize a Java object into a YAML string.
     *
     * @param object Java object.
     * @return YAML string.
     */
    public static String dumpObject(Object object) {
        return yaml.dumpAsMap(object);
    }
    
    /**
     * Parse YAML String and produce the corresponding Java object (Standard Java classes and in YamlParserConstructor
     * specified Construct).
     *
     * @param content YAML String
     * @param type    Java object.
     * @param <T>     Java object type.
     * @return Java object.
     */
    public static <T> T loadObject(String content, Class<T> type) {
        return yaml.loadAs(content, type);
    }
    
    public static class YamlParserConstructor extends SafeConstructor {
        
        public static Tag configMetadataTag = new Tag(ConfigMetadata.class);
        
        public YamlParserConstructor() {
            super();
            yamlConstructors.put(configMetadataTag, new ConstructYamlConfigMetadata());
        }
    }
    
    public static class ConstructYamlConfigMetadata extends AbstractConstruct {
        
        @Override
        public Object construct(Node node) {
            if (!YamlParserConstructor.configMetadataTag.getValue().equals(node.getTag().getValue())) {
                throw new NacosRuntimeException(NacosException.INVALID_PARAM,
                        "could not determine a constructor for the tag " + node.getTag() + node.getStartMark());
            }
            
            MappingNode mNode = (MappingNode) node;
            List<NodeTuple> value = mNode.getValue();
            if (CollectionUtils.isEmpty(value)) {
                return null;
            }
            NodeTuple nodeTuple = value.get(0);
            ConfigMetadata configMetadata = new ConfigMetadata();
            SequenceNode sequenceNode = (SequenceNode) nodeTuple.getValueNode();
            if (CollectionUtils.isEmpty(sequenceNode.getValue())) {
                return configMetadata;
            }
            
            List<ConfigMetadata.ConfigExportItem> exportItems = sequenceNode.getValue().stream().map(itemValue -> {
                ConfigMetadata.ConfigExportItem configExportItem = new ConfigMetadata.ConfigExportItem();
                MappingNode itemMap = (MappingNode) itemValue;
                List<NodeTuple> propertyValues = itemMap.getValue();
                Map<String, String> metadataMap = new HashMap<>(propertyValues.size());
                propertyValues.forEach(metadata -> {
                    ScalarNode keyNode = (ScalarNode) metadata.getKeyNode();
                    ScalarNode valueNode = (ScalarNode) metadata.getValueNode();
                    metadataMap.put(keyNode.getValue(), valueNode.getValue());
                });
                configExportItem.setDataId(metadataMap.get("dataId"));
                configExportItem.setGroup(metadataMap.get("group"));
                configExportItem.setType(metadataMap.get("type"));
                configExportItem.setDesc(metadataMap.get("desc"));
                configExportItem.setAppName(metadataMap.get("appName"));
                return configExportItem;
            }).collect(Collectors.toList());
            
            configMetadata.setMetadata(exportItems);
            return configMetadata;
        }
    }
    
}
