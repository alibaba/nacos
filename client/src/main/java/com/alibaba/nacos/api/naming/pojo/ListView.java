package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.fastjson.JSON;

import java.util.List;

/**
 * @author dungu.zpf
 */
public class ListView<T> {

    private List<T> data;
    private int count;

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
