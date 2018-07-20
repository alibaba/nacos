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
package com.alibaba.nacos.config.mock;

import java.util.Enumeration;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;



public class FilterConfigMock implements FilterConfig {

    public FilterConfigMock(ServletContext context) {
        this.context = context;
    }
    
    
    @Override
    public String getFilterName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        return context;
    }

    @Override
    public String getInitParameter(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Enumeration getInitParameterNames() {
        // TODO Auto-generated method stub
        return null;
    }

    final ServletContext context;
}
