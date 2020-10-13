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

package com.alibaba.nacos.config.server.modules.mapstruct;

import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTagEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

/**
 * ConfigInfo4TagMapStruct.
 *
 * @author Nacos
 */
@Mapper
public interface ConfigInfo4TagMapStruct {
    
    ConfigInfo4TagMapStruct INSTANCE = Mappers.getMapper(ConfigInfo4TagMapStruct.class);
    
    @Mappings({@Mapping(source = "tenantId", target = "tenant"), @Mapping(source = "groupId", target = "group")})
    ConfigInfo4Tag convertConfigInfo4Tag(ConfigInfoTagEntity configInfoTagEntity);
    
}
