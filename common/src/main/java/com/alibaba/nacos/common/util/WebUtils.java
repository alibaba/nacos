package com.alibaba.nacos.common.util;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class WebUtils {

    public static String required(HttpServletRequest req, String key) {
        String value = req.getParameter(key);
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Param '" + key + "' is required.");
        }

        String encoding = req.getParameter("encoding");
        if (!StringUtils.isEmpty(encoding)) {
            try {
                value = new String(value.getBytes("UTF-8"), encoding);
            } catch (UnsupportedEncodingException ignore) {
            }
        }

        return value.trim();
    }

    public static String optional(HttpServletRequest req, String key, String defaultValue) {

        if (!req.getParameterMap().containsKey(key) || req.getParameterMap().get(key)[0] == null) {
            return defaultValue;
        }

        String value = req.getParameter(key);

        String encoding = req.getParameter("encoding");
        if (!StringUtils.isEmpty(encoding)) {
            try {
                value = new String(value.getBytes("UTF-8"), encoding);
            } catch (UnsupportedEncodingException ignore) {
            }
        }

        return value.trim();
    }

    public static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), "UTF-8");
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }
}
