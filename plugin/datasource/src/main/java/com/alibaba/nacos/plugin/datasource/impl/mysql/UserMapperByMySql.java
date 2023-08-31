package com.alibaba.nacos.plugin.datasource.impl.mysql;

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
 * The mysql implementation of UserMapper.
 *
 * @author hkm
 **/
public class UserMapperByMySql extends AbstractMapper implements UserMapper {

    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
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
                sqlFetchRows + where + " LIMIT "
                        + context.getStartRow() + "," + context.getPageSize();
        return new MapperResult(sql, paramList);
    }
}
