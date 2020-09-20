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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * Global exception handler.
 *
 * @author Nacos
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * For IllegalArgumentException, we are returning void with status code as 400, so our error-page will be used in
     * this case.
     *
     * @throws IllegalArgumentException IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(Exception ex) throws IOException {
        MetricsMonitor.getIllegalArgumentException().increment();
        return ResponseEntity.status(400).body(ExceptionUtil.getAllExceptionMsg(ex));
    }
    
    /**
     * For NacosException.
     *
     * @throws NacosException NacosException.
     */
    @ExceptionHandler(NacosException.class)
    public ResponseEntity<String> handleNacosException(NacosException ex) throws IOException {
        MetricsMonitor.getNacosException().increment();
        return ResponseEntity.status(ex.getErrCode()).body(ExceptionUtil.getAllExceptionMsg(ex));
    }
    
    /**
     * For DataAccessException.
     *
     * @throws DataAccessException DataAccessException.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> handleDataAccessException(DataAccessException ex) throws DataAccessException {
        MetricsMonitor.getDbException().increment();
        return ResponseEntity.status(500).body(ExceptionUtil.getAllExceptionMsg(ex));
    }
}
