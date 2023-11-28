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

package com.alibaba.nacos.config.server.paramcheck;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigListener http param extractor.
 *
 * @author zhuoguang
 */
public class ConfigListenerHttpParamExtractor extends AbstractHttpParamExtractor {
    
    static final char WORD_SEPARATOR_CHAR = (char) 2;
    
    static final char LINE_SEPARATOR_CHAR = (char) 1;
    
    @Override
    public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosRuntimeException {
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        String listenConfigs = request.getParameter("Listening-Configs");
        if (StringUtils.isBlank(listenConfigs)) {
            return paramInfos;
        }
        try {
            listenConfigs = URLDecoder.decode(listenConfigs, Constants.ENCODE);
        } catch (UnsupportedEncodingException e) {
            throw new NacosRuntimeException(ErrorCode.UnKnowError.getCode(), e);
        }
        if (StringUtils.isBlank(listenConfigs)) {
            return paramInfos;
        }
        String[] lines = listenConfigs.split(Character.toString(LINE_SEPARATOR_CHAR));
        for (String line : lines) {
            ParamInfo paramInfo = new ParamInfo();
            String[] words = line.split(Character.toString(WORD_SEPARATOR_CHAR));
            if (words.length < 2 || words.length > 4) {
                throw new IllegalArgumentException("invalid probeModify");
            }
            paramInfo.setDataId(words[0]);
            paramInfo.setGroup(words[1]);
            if (words.length == 4) {
                paramInfo.setNamespaceId(words[3]);
            }
            paramInfos.add(paramInfo);
        }
        return paramInfos;
    }
}
