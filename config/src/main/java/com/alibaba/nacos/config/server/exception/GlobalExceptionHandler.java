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
package com.alibaba.nacos.config.server.exception;

import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * global exception handler
 *
 * @author Nacos
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * For IllegalArgumentException, we are returning void with status code as 400, so our error-page will be used in
     * this case.
     *
     * @throws IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgumentException(HttpServletResponse response, Exception ex) throws IOException {
        MetricsMonitor.getIllegalArgumentException().increment();
        response.setStatus(400);
        if (ex.getMessage() != null) {
            response.getWriter().println(ex.getMessage());
        } else {
            response.getWriter().println("invalid param");
        }
    }

    /**
     * For NacosException
     *
     * @throws NacosException
     */
    @ExceptionHandler(NacosException.class)
    public void handleNacosException(HttpServletResponse response, NacosException ex) throws IOException {
        MetricsMonitor.getNacosException().increment();
        response.setStatus(ex.getErrCode());
        if (ex.getErrMsg() != null) {
            response.getWriter().println(ex.getErrMsg());
        } else {
            response.getWriter().println("unknown exception");
        }
    }

    /**
     * For DataAccessException
     *
     * @throws DataAccessException
     */
    @ExceptionHandler(DataAccessException.class)
    public void handleDataAccessException(HttpServletResponse response, DataAccessException ex) throws DataAccessException {
        MetricsMonitor.getDbException().increment();
        throw new CannotGetJdbcConnectionException(ex.getMessage());
    }

}
