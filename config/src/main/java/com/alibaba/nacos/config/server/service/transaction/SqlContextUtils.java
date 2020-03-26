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

package com.alibaba.nacos.config.server.service.transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SqlContextUtils {

    private static final ThreadLocal<ArrayList<ModifyRequest>> SQL_CONTEXT =
            ThreadLocal.withInitial(ArrayList::new);

    public static void addSqlContext(String sql, Object... args) {
        ArrayList<ModifyRequest> ModifyRequests = SQL_CONTEXT.get();
        ModifyRequest context = new ModifyRequest();
        context.setExecuteNo(ModifyRequests.size());
        context.setSql(sql);
        context.setArgs(args);
        ModifyRequests.add(context);
        SQL_CONTEXT.set(ModifyRequests);
    }

    public static List<ModifyRequest> getCurrentSqlContext() {
        return SQL_CONTEXT.get();
    }

    public static void cleanCurrentSqlContext() {
        SQL_CONTEXT.remove();
    }

}
