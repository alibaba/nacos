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

package com.alibaba.nacos.core.auth.condition;

import static com.alibaba.nacos.sys.env.Constants.REQUEST_PATH_SEPARATOR;

/**
 * request path info. method:{@link org.springframework.web.bind.annotation.RequestMapping#method()} path: {@link
 * org.springframework.web.bind.annotation.RequestMapping#value()} or {@link org.springframework.web.bind.annotation.RequestMapping#value()}
 *
 * @author horizonzy
 * @since 1.3.2
 */
public class PathRequestCondition {
    
    private final PathExpression pathExpression;
    
    public PathRequestCondition(String pathExpression) {
        this.pathExpression = parseExpressions(pathExpression);
    }
    
    private PathExpression parseExpressions(String pathExpression) {
        String[] split = pathExpression.split(REQUEST_PATH_SEPARATOR);
        String method = split[0];
        String path = split[1];
        return new PathExpression(method, path);
    }
    
    @Override
    public String toString() {
        return "PathRequestCondition{" + "pathExpression=" + pathExpression + '}';
    }
    
    static class PathExpression {
        
        private final String method;
        
        private final String path;
        
        PathExpression(String method, String path) {
            this.method = method;
            this.path = path;
        }
        
        @Override
        public String toString() {
            return "PathExpression{" + "method='" + method + '\'' + ", path='" + path + '\'' + '}';
        }
    }
}
