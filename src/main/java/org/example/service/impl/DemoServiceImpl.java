package org.example.service.impl;

import org.example.mvcFramework.annotation.RService;
import org.example.service.DemoService;

/**
 * @Description TODO
 * @Author ooooor
 * @Date 2020/3/4 23:16
 **/
@RService
public class DemoServiceImpl implements DemoService {

    @Override
    public String get(String name) {
        return "my name is" + name;
    }
}
