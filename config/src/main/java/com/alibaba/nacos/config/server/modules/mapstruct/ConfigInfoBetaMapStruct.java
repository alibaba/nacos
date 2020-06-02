package com.alibaba.nacos.config.server.modules.mapstruct;

import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBeta;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ConfigInfoBetaMapStruct {

    ConfigInfoBetaMapStruct MAPPER = Mappers.getMapper(ConfigInfoBetaMapStruct.class);

    ConfigInfoBase convertConfigInfoBase(ConfigInfoBeta configInfoBeta);

    ConfigInfo4Beta convertConfigInfo4Beta(ConfigInfoBeta configInfoBeta);
}
