/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.filter;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Nacos server execute filter. To exclude some beans or features by config
 *
 * @author xiweng.yy
 */
public class NacosTypeExcludeFilter implements TypeFilter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosTypeExcludeFilter.class);
    
    private final Map<String, NacosPackageExcludeFilter> packageExcludeFilters;
    
    public NacosTypeExcludeFilter() {
        this.packageExcludeFilters = new HashMap<>(2);
        for (NacosPackageExcludeFilter each : NacosServiceLoader.load(NacosPackageExcludeFilter.class)) {
            packageExcludeFilters.put(each.getResponsiblePackagePrefix(), each);
            LOGGER.info("Load Nacos package exclude filter success, package prefix {}, filter {}",
                    each.getResponsiblePackagePrefix(), each.getClass().getCanonicalName());
        }
    }
    
    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        // If no exclude filters, all classes should be load.
        if (packageExcludeFilters.isEmpty()) {
            return false;
        }
        boolean isSpringBootApplication = metadataReader.getAnnotationMetadata()
                .hasAnnotation(SpringBootApplication.class.getCanonicalName());
        String className = metadataReader.getClassMetadata().getClassName();
        if (isSpringBootApplication) {
            LOGGER.info("Skip @SpringBootApplication annotation for class {} to avoid duplicate scan", className);
            return true;
        }
        for (Map.Entry<String, NacosPackageExcludeFilter> entry : packageExcludeFilters.entrySet()) {
            // If match the package exclude filter, judged by filter.
            if (className.startsWith(entry.getKey())) {
                Set<String> annotations = metadataReader.getAnnotationMetadata().getAnnotationTypes();
                return entry.getValue().isExcluded(className, annotations);
            }
        }
        // No match filter, load class
        return false;
    }
}
