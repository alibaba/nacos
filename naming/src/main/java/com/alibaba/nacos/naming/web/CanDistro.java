package com.alibaba.nacos.naming.web;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to determine if method should be redirected.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface CanDistro {
}
