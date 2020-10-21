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

package com.alibaba.nacos.config.server.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derby util.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DerbyUtils {
    
    private static final String INSERT_INTO_VALUES = "(INSERT INTO .+? VALUES)";
    
    private static final Pattern INSERT_INTO_PATTERN = Pattern.compile(INSERT_INTO_VALUES);
    
    /**
     * Because Derby's database table name is uppercase, you need to do a conversion to the insert statement that was
     * inserted.
     *
     * @param sql external database insert sql
     * @return derby insert sql
     */
    public static String insertStatementCorrection(String sql) {
        Matcher matcher = INSERT_INTO_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql;
        }
        final String target = matcher.group(0);
        final String upperCase = target.toUpperCase().replace("`", "");
        return sql.replaceFirst(INSERT_INTO_VALUES, upperCase).replace(";", "");
    }
    
}
