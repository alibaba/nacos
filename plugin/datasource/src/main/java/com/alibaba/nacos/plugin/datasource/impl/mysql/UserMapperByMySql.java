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
 * <p>
 *
 * </p>
 *
 * @author huangKeMing
 * @since 2023/8/31
 */
public class UserMapperByMySql extends AbstractMapper implements UserMapper {

    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }

    @Override
    public MapperResult getUsers(MapperContext context) {
        StringBuilder sql = new StringBuilder("SELECT username,password FROM users ");
        String username = context.getWhereParameter(FieldConstant.USER_NAME).toString();
        List<Object> paramList = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            sql.append(" WHERE AND username = ? ");
            paramList.add(username);
        }

        int startRow = context.getStartRow();
        int pageSize = context.getPageSize();
        sql.append(" LIMIT " + startRow + "," + pageSize );

        paramList.add(startRow);
        paramList.add(pageSize);

        return new MapperResult(sql.toString(), paramList);
    }
}
