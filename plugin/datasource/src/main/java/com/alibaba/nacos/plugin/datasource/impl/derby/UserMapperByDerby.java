package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.UserMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The derby implementation of UserMapper.
 *
 * @author hkm
 **/
public class UserMapperByDerby extends AbstractMapper implements UserMapper {

    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }

    @Override
    public MapperResult getUsers(MapperContext context) {
        final String sqlFetchRows = "SELECT username,password FROM users ";
        final String username = context.getWhereParameter(FieldConstant.USER_NAME).toString();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> paramList = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username = ? ");
            paramList.add(username);
        }

        String sql =
                sqlFetchRows + where + " OFFSET " + context.getStartRow() + " ROWS FETCH NEXT " + context.getPageSize()
                        + " ROWS ONLY";
        return new MapperResult(sql, paramList);
    }
}
