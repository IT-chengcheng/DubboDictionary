package org.apache.dubbo.common.bytecode;

/**
 * @author:chengcheng
 * @date:2021.01.31
 */
public class Proxy0 implements org.apache.dubbo.demo.DemoService, org.apache.dubbo.rpc.service.Destroyable {

    public static java.lang.reflect.Method[] methods;

    private java.lang.reflect.InvocationHandler handler;

    public Proxy0() {
    }

    public Proxy0(java.lang.reflect.InvocationHandler arg0) {
        handler = arg0;
    }
    public java.lang.String sayHello(java.lang.String arg0) {
        Object[] args = new Object[1];
        args[0] = arg0;
        Object ret = null;
        try {
            ret = handler.invoke(this, methods[2], args);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return (java.lang.String) ret;
    }

    public java.util.concurrent.CompletableFuture sayHelloAsync(java.lang.String arg0) {
        Object[] args = new Object[1];
        args[0] = arg0;
        Object ret = null;
        try {
            ret = handler.invoke(this, methods[3], args);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return (java.util.concurrent.CompletableFuture) ret;
    }
    public void $destroy() {
        Object[] args = new Object[0];
        try {
            Object ret = handler.invoke(this, methods[0], args);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public java.lang.Object $echo(java.lang.Object arg0) {
        Object[] args = new Object[1];
        args[0] = arg0;
        Object ret = null;
        try {
            ret = handler.invoke(this, methods[1], args);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return (java.lang.Object) ret;
    }


}
