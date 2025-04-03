/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.springdoc.openapi;

import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.common.utils.StringUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import java.util.Map;

/**
 * spring doc nacos tag sort open api customizer.
 *
 * @author xiweng.yy
 */
public class NacosTagSorterOpenApiCustomizer implements GlobalOpenApiCustomizer {
    
    @Override
    public void customise(OpenAPI openApi) {
        openApi.getTags().sort((o1, o2) -> {
            String tag1Module = getModule(o1);
            String tag2Module = getModule(o2);
            if (StringUtils.equals(tag1Module, tag2Module)) {
                return 0;
            }
            if (null == tag1Module) {
                return -1;
            }
            if (null == tag2Module) {
                return 1;
            }
            return (-1) * tag1Module.compareTo(tag2Module);
        });
    }
    
    private String getModule(Tag tag) {
        if (null == tag.getExtensions()) {
            return null;
        }
        Map<String, String> extensionMap = (Map<String, String>) tag.getExtensions().get("x-" + RemoteConstants.LABEL_MODULE);
        if (null == extensionMap) {
            return null;
        }
        return extensionMap.get(RemoteConstants.LABEL_MODULE);
    }
}
