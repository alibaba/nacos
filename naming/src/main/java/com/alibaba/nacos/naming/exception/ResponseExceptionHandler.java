package com.alibaba.nacos.naming.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author dungu.zpf
 */
@ControllerAdvice
public class ResponseExceptionHandler {

    @ExceptionHandler(NacosException.class)
    private ResponseEntity<String> handleNacosException(NacosException e) {
        return ResponseEntity.status(e.getErrorCode()).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleParameterError(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingParams(MissingServletRequestParameterException ex) {
        String name = ex.getParameterName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parameter '" + name + "' is missing");
    }
}
