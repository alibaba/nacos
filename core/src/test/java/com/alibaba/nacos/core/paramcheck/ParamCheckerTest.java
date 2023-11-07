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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * ParamCheckerTest.
 * @author 985492783@qq.com
 * @date 2023/11/7 20:16
 */
public class ParamCheckerTest {
    
    @Test
    public void testCheckAnnotation() {
        ParamChecker.Checker checker = Controller.class.getAnnotation(ParamChecker.Checker.class);
        AbstractRpcParamExtractor rpcChecker = ParamChecker.getRpcChecker(checker);
        assertEquals(rpcChecker.getClass().getSimpleName(), ConfigRequestParamExtractor.class.getSimpleName());
    }
    
    @ParamChecker.Checker(rpcChecker = ConfigRequestParamExtractor.class)
    public static class Controller {
        
        public void testCheckNull() {
        
        }
        
        @ParamChecker.Checker(httpChecker = TestHttpChecker.class)
        public void testCheck() {
        
        }
    }
    
    public static class TestHttpChecker extends AbstractHttpParamExtractor {
    
        @Override
        public List<ParamInfo> extractParam(HttpServletRequest params) throws Exception {
            List<ParamInfo> list = new ArrayList<>();
            ParamInfo paramInfo = new ParamInfo();
            paramInfo.setDataId(params.getParameter("dataId"));
            list.add(paramInfo);
            return list;
        }
    }
}
