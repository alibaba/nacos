package com.alibaba.nacos.plugin.datasource.mapper;

import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The user info mapper.
 *
 * @author hkm
 **/

public interface UserMapper extends Mapper {

    /**
     * used to select user info.
     *
     * <p>Example: SELECT username,password FROM users WHERE username>? LIMIT ?;
     *
     * @param context sql paramMap
     * @return sql.
     */
    MapperResult getUsers(MapperContext context);

    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.USERS;
    }
}
