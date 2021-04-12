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

import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class YamlParserUtil {
    
    private static Yaml yaml;
    
    static {
        Representer representer = new Representer() {
            
            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue,
                    Tag customTag) {
                if (propertyValue == null) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        yaml = new Yaml(representer);
    }
    
    public static String dumpObject(Object object) {
        return yaml.dumpAsMap(object);
    }
    
    public static <T> T loadObject(String content, Class<T> type) {
        return yaml.loadAs(content, type);
    }
    
}
