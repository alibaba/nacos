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

import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * ConfigInfoEntityMapStruct.
 *
 * @author Nacos
 */
@Mapper
public interface ConfigInfoEntityMapStruct {
    
    ConfigInfoEntityMapStruct INSTANCE = Mappers.getMapper(ConfigInfoEntityMapStruct.class);
    
    List<ConfigAllInfo> convertConfigAllInfoList(List<ConfigInfoEntity> list);
    
    @Mappings({@Mapping(source = "groupId", target = "group")})
    ConfigInfoBase convertConfigInfoBase(ConfigInfoEntity configInfo);
    
    @Mappings({@Mapping(target = "groupId", source = "group"), @Mapping(target = "tenantId", source = "tenant")})
    ConfigInfoEntity convertConfigInfoEntity(ConfigInfo configInfo);
}
