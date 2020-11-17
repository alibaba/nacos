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

package com.alibaba.nacos.console.exception;

import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Exception handler for console module.
 *
 * @author nkorange
 * @since 1.2.0
 */
@ControllerAdvice
public class ConsoleExceptionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleExceptionHandler.class);
    
    @ExceptionHandler(AccessException.class)
    private ResponseEntity<String> handleAccessException(AccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getErrMsg());
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    private ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ExceptionUtil.getAllExceptionMsg(e));
    }
    
    @ExceptionHandler(Exception.class)
    private ResponseEntity<String> handleException(Exception e) {
        LOGGER.error("CONSOLE", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ExceptionUtil.getAllExceptionMsg(e));
    }
}
