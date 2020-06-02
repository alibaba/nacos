package com.alibaba.nacos.config.server.modules.mapstruct;

import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ConfigInfoMapStruct {

    ConfigInfoMapStruct MAPPER = Mappers.getMapper(ConfigInfoMapStruct.class);


    List<ConfigAllInfo> convertConfigAllInfoList(List<ConfigInfo> list);

    @Mappings({
        @Mapping(source="groupId", target="group")
    })
    ConfigInfoBase convertConfigInfoBase(ConfigInfo configInfo);
}
