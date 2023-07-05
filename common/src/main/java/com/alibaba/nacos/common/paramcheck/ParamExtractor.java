package com.alibaba.nacos.common.paramcheck;

import java.util.List;

/**
 * ParamExtractor interface
 *
 * @author sunrisea
 */
public interface ParamExtractor<T> {

    public List<String> getTargetRequestList();

    public void extractParamAndCheck(T params) throws Exception;
}
