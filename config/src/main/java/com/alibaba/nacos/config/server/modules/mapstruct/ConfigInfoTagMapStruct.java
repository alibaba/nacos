package com.alibaba.nacos.config.server.modules.mapstruct;

import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTag;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ConfigInfoTagMapStruct {

    ConfigInfoTagMapStruct MAPPER = Mappers.getMapper(ConfigInfoTagMapStruct.class);


    ConfigInfoBase convertConfigInfoBase(ConfigInfoTag configInfoTag);
}
