package org.apache.dubbo.common.bytecode;

import org.apache.dubbo.demo.GreetingService;

public class Wrapper0 extends org.apache.dubbo.common.bytecode.Wrapper


{
    public static String[] pns;

    public static java.util.Map pts;

    public static String[] mns; //方法名

    public static String[] dmns;

    public static Class[] mts0;

    public String[] getPropertyNames() {
        return pns;
    }

    public boolean hasProperty(String n) {
        return pts.containsKey(n);
    }

    public Class getPropertyType(String n) {
        return (Class) pts.get(n);
    }

    public String[] getMethodNames() {
        return mns;
    }

    public String[] getDeclaredMethodNames() {
        return dmns;
    }

    public void setPropertyValue(Object o, String n, Object v) {
        org.apache.dubbo.demo.GreetingService w;
        try {
            w = ((org.apache.dubbo.demo.GreetingService) o);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        if (n.equals("testgaga")) {
            w.setTestgaga((java.lang.String) v);
            return;
        }
        throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException("Not found property \"" + n + "\" field or setter method in class org.apache.dubbo.demo.GreetingService.");
    }

    public Object getPropertyValue(Object o, String n) {
        org.apache.dubbo.demo.GreetingService w;
        try {
            w = ((org.apache.dubbo.demo.GreetingService) o);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException("Not found property \"" + n + "\" field or getter method in class org.apache.dubbo.demo.GreetingService.");
    }

    public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws java.lang.reflect.InvocationTargetException {
        // 入参依次为：接口实现类，方法名，方法类型数组，方法入参数组
        org.apache.dubbo.demo.GreetingService w;
        try {
            w = ((org.apache.dubbo.demo.GreetingService) o);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        try {
            if ("setTestgaga".equals(n) && p.length == 1) {
                w.setTestgaga((java.lang.String) v[0]);
                return null;
            }
            if ("hello".equals(n) && p.length == 0) {
                return w.hello();
            }
            if ("haveNoReturn".equals(n) && p.length == 0) {
                w.haveNoReturn();
                return null;
            }
            if( "getTestddd".equals( n )  &&  p.length == 1 ) {
                return w.getTestddd((java.lang.String)v[0]);
            }
        } catch (Throwable e) {
            throw new java.lang.reflect.InvocationTargetException(e);
        }
        throw new org.apache.dubbo.common.bytecode.NoSuchMethodException("Not found method \"" + n + "\" in class org.apache.dubbo.demo.GreetingService.");
    }
}
