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

package com.alibaba.nacos.server;

import com.alibaba.nacos.core.web.NacosWebBean;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract TypeFilter to filter Nacos Web Bean or not.
 *
 * @author xiweng.yy
 */
public abstract class AbstractNacosWebBeanTypeFilter implements TypeFilter {
    
    private static final Set<String> WEB_BEAN_ANNOTATIONS = new HashSet<>();
    
    static {
        WEB_BEAN_ANNOTATIONS.add(RestController.class.getCanonicalName());
        WEB_BEAN_ANNOTATIONS.add(ControllerAdvice.class.getCanonicalName());
        WEB_BEAN_ANNOTATIONS.add(Controller.class.getCanonicalName());
        WEB_BEAN_ANNOTATIONS.add(NacosWebBean.class.getCanonicalName());
    }
    
    protected boolean isWebBean(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        for (String each : WEB_BEAN_ANNOTATIONS) {
            if (metadataReader.getAnnotationMetadata().hasAnnotation(each)) {
                return true;
            }
        }
        return false;
    }
}
