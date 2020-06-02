package com.alibaba.nacos.config.server.modules.mapstruct;

import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.modules.entity.HisConfigInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper
public interface HisConfigInfoMapStruct {


    HisConfigInfoMapStruct MAPPER = Mappers.getMapper(HisConfigInfoMapStruct.class);


    @Mappings({
        @Mapping(source="groupId", target="group"),
        @Mapping(source="tenantId", target="tenant"),
        @Mapping(source="gmtCreate", target="createdTime"),
        @Mapping(source="gmtModified", target="lastModifiedTime")
    })
    ConfigHistoryInfo convertConfigHistoryInfo(HisConfigInfo hisConfigInfo);

}
