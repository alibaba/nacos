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

package com.alibaba.nacos.sys.env;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support for configuring automatic refresh and loading into the Environment.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Deprecated
public class NacosAutoRefreshPropertySourceLoader implements PropertySourceLoader {
    
    private final Map<String, Object> properties = new ConcurrentHashMap<>(16);
    
    private Resource holder = null;
    
    @Override
    public String[] getFileExtensions() {
        return new String[] {"properties"};
    }
    
    @Override
    public List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        holder = resource;
        Map<String, ?> tmp = loadProperties(resource);
        properties.putAll(tmp);
        
        try {
            WatchFileCenter.registerWatcher(EnvUtil.getConfPath(), new FileWatcher() {
                @Override
                public void onChange(FileChangeEvent event) {
                    try {
                        Map<String, ?> tmp1 = loadProperties(holder);
                        properties.putAll(tmp1);
                    } catch (IOException ignore) {
                    
                    }
                }
                
                @Override
                public boolean interest(String context) {
                    return StringUtils.contains(context, "application.properties");
                }
            });
        } catch (NacosException ignore) {
        
        }
        
        if (properties.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new OriginTrackedMapPropertySource("nacos_application_conf", properties));
    }
    
    private Map<String, ?> loadProperties(Resource resource) throws IOException {
        return new OriginTrackedPropertiesLoader(resource).load();
    }
    
    @JustForTest
    protected Map<String, Object> getProperties() {
        return properties;
    }
}
