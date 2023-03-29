/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.model.button.AbstractPropertyNode;
import com.alibaba.nacos.console.service.PropertyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * console property controller.
 * @author 985492783@qq.com
 * @date 2023/3/27 16:32
 */
@NacosApi
@RestController
@RequestMapping("/v2/console/property")
public class PropertyControllerV2 {
    
    private final PropertyService propertyService;
    
    public PropertyControllerV2(PropertyService propertyService) {
        this.propertyService = propertyService;
    }
    
    @GetMapping("/list")
    public Result<Map<String, List<AbstractPropertyNode<?>>>> getPropertyList() {
        return Result.success(propertyService.getPropertyMap());
    }
   
}
