package com.zk.demo.xservice.impl;

import com.zk.demo.xservice.ITestXService;
import com.zk.xspring.annotation.XService;

/**
 * <p>
 * 业务实现类
 *
 */
@XService
public class TestXServiceImpl implements ITestXService {

    public String listClassName() {

        // 假装来自数据库
        return "123456TestXServiceImpl";
    }
}
