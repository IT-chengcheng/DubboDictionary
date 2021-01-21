package org.apache.dubbo.demo.provider;

import org.apache.dubbo.demo.IEat;

/**
 * @author:chengcheng
 * @date:2021.01.21
 */
public class EatIml implements IEat {
    @Override
    public void eat() {
        System.out.println("服务端开吃");
    }
}
