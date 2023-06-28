package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;

/**
 * The parent class of the all DerbyMappers.
 *
 * @author 高露
 **/

public abstract class AbstractDerbyMapper extends AbstractMapper {

    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }
}
