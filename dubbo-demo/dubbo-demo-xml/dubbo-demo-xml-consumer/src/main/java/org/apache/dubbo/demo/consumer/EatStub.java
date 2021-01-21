package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.demo.IEat;

/**
 * @author:chengcheng
 * @date:2021.01.21
 */
public class EatStub implements IEat {

    private IEat sub;

    public  EatStub(IEat sub){
        this.sub = sub;
    }

    @Override
    public void eat() {

        System.out.println("进入客户端 sub");
        this.sub.eat();
    }
}
