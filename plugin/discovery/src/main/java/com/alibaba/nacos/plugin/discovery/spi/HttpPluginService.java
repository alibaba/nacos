/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.discovery.spi;

import com.alibaba.nacos.plugin.discovery.wapper.HttpServletWapper;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Http Service spi.
 *
 * @author karsonto
 */
public interface HttpPluginService {
    
    String getRequestUri();
    
    void init(ApplicationContext applicationContext);
    
    void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
    
    boolean enable();
    
    void bind(HttpServletWapper servletWapper);
    
}
