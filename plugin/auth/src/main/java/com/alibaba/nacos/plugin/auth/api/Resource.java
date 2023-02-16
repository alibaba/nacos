/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.api;

import com.alibaba.nacos.common.utils.StringUtils;

import java.io.Serializable;
import java.util.Properties;

/**
 * Resource used in authorization.
 *
 * @author nkorange
 * @author mai.jh
 * @since 1.2.0
 */
public class Resource implements Serializable {
    
    private static final long serialVersionUID = 925971662931204553L;
    
    public static final Resource EMPTY_RESOURCE = new Resource(StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY,
            StringUtils.EMPTY, null);
    
    private final String namespaceId;
    
    private final String group;
    
    private final String name;
    
    private final String type;
    
    private final Properties properties;
    
    public Resource(String namespaceId, String group, String name, String type, Properties properties) {
        this.namespaceId = namespaceId;
        this.group = group;
        this.name = name;
        this.type = type;
        this.properties = properties;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public Properties getProperties() {
        return properties;
    }
    
    @Override
    public String toString() {
        return "Resource{" + "namespaceId='" + namespaceId + '\'' + ", group='" + group + '\'' + ", name='" + name
                + '\'' + ", type='" + type + '\'' + ", properties=" + properties + '}';
    }
}
