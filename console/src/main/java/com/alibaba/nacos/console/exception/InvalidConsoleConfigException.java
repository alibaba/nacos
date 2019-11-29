package com.alibaba.nacos.console.exception;

/**
 * InvalidConsoleConfigException
 * Exception for the config of Nacos console
 * @author Nacos
 */
public class InvalidConsoleConfigException extends RuntimeException {

    private static final String MSG_TEMPLATE = "The nacos console config {%s}'s value  is invalid ,please modify the config and restart the server. info:{%s}";

    public InvalidConsoleConfigException(String propertyName, String errorInfo) {
        super(String.format(MSG_TEMPLATE, propertyName, errorInfo));
    }
}
