/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.paramcheck;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Param checker manager.
 *
 * @author zhuoguang
 */
public class ParamCheckerManager {
    
    private static final ParamCheckerManager INSTANCE = new ParamCheckerManager();
    
    private static final AbstractParamChecker DEFAULT_PARAM_CHECKER = new DefaultParamChecker();
    
    private final Map<String, AbstractParamChecker> paramCheckerMap = new ConcurrentHashMap<>();
    
    private ParamCheckerManager() {
        Collection<AbstractParamChecker> paramCheckers = NacosServiceLoader.load(AbstractParamChecker.class);
        for (AbstractParamChecker paramChecker : paramCheckers) {
            String checkerType = paramChecker.getCheckerType();
            paramCheckerMap.put(checkerType, paramChecker);
        }
    }
    
    public static ParamCheckerManager getInstance() {
        return INSTANCE;
    }
    
    public AbstractParamChecker getParamChecker(String checkerType) {
        if (StringUtils.isBlank(checkerType)) {
            return DEFAULT_PARAM_CHECKER;
        }
        AbstractParamChecker paramChecker = paramCheckerMap.get(checkerType);
        if (paramChecker == null) {
            paramChecker = DEFAULT_PARAM_CHECKER;
        }
        return paramChecker;
    }
}
