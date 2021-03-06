/**
 * ****************************************************************************
 * (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0 which accompany this distribution.
 * <p/>
 * The Apache License is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * *****************************************************************************
 */
package io.cloudslang.lang.entities.bindings.values;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;
import org.apache.commons.lang.ClassUtils;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyType;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * PyObjectValue proxy factory
 *
 * Created by Ifat Gavish on 04/05/2016
 */
public class PyObjectValueProxyFactory {

    private static final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private static final Lock writeLock = readWriteLock.writeLock();

    public static final String PROXY_CLASS_SUFFIX = "Value";

    private static ConcurrentMap<String, PyObjectValueProxyClass> proxyClasses = new ConcurrentHashMap<>();

    public static PyObjectValue create(Serializable content, boolean sensitive) {
        PyObject pyObject = Py.java2py(content);
        try {
            PyObjectValueProxyClass proxyClass = getProxyClass(pyObject);
            PyObjectValue pyObjectValue = (PyObjectValue)proxyClass.getConstructor().newInstance(proxyClass.getParams());
            ((Proxy)pyObjectValue).setHandler(new PyObjectValueMethodHandler(content, sensitive, pyObject));
            return pyObjectValue;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create a proxy to new instance for PyObjectValue and " + pyObject.getClass().getSimpleName(), e);
        }
    }

    private static PyObjectValueProxyClass getProxyClass(PyObject pyObject) throws Exception {
        String proxyClassName = pyObject.getClass() + PROXY_CLASS_SUFFIX;
        PyObjectValueProxyClass proxyClass = proxyClasses.get(proxyClassName);
        if (proxyClass == null) {
            writeLock.lock();
            try {
                proxyClass = proxyClasses.get(proxyClassName);
                if (proxyClass == null) {
                    ProxyFactory factory = new ProxyFactory();
                    factory.setSuperclass(pyObject.getClass());
                    factory.setInterfaces(new Class[]{PyObjectValue.class});
                    factory.setFilter(new PyObjectValueMethodFilter());
                    proxyClasses.putIfAbsent(proxyClassName, createProxyClass(factory.createClass(), pyObject));
                    proxyClass = proxyClasses.get(proxyClassName);
                }
            } finally {
                writeLock.unlock();
            }
        }
        return proxyClass;
    }
    private static PyObjectValueProxyClass createProxyClass(Class proxyClass, PyObject pyObject) throws Exception {
        Constructor<?> constructor = proxyClass.getConstructors()[0];
        for (Constructor<?> con : proxyClass.getConstructors()) {
            if (con.getParameterTypes().length < constructor.getParameterTypes().length) {
                constructor = con;
            }
        }
        Object[] params = new Object[constructor.getParameterTypes().length];
        for (int index = 0; index < constructor.getParameterTypes().length; index++) {
            Class<?> parameterType = constructor.getParameterTypes()[index];
            params[index] = parameterType.equals(PyType.class) ? pyObject.getType() :
                    !parameterType.isPrimitive() ? null : getPrimitiveTypeDefaultValue(parameterType);
        }
        return new PyObjectValueProxyClass(proxyClass, constructor, params);
    }

    @SuppressWarnings("unchecked")
    private static Object getPrimitiveTypeDefaultValue(Class<?> parameterType) throws Exception {
        return ClassUtils.primitiveToWrapper(parameterType).getConstructor(String.class).newInstance("0");
    }

    private static class PyObjectValueMethodFilter implements MethodFilter {

        @Override
        public boolean isHandled(Method method) {
            return Modifier.isPublic(method.getModifiers());
        }
    }

    private static class PyObjectValueMethodHandler implements MethodHandler, Serializable {

        private static final String ACCESSED_GETTER_METHOD = "isAccessed";

        private Value value;
        private PyObject pyObject;
        private boolean accessed;

        public PyObjectValueMethodHandler(Serializable content, boolean sensitive, PyObject pyObject) {
            this.value = ValueFactory.create(content, sensitive);
            this.pyObject = pyObject;
            this.accessed = false;
        }

        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
            if (thisMethod.getName().equals(ACCESSED_GETTER_METHOD)) {
                return accessed;
            } else if (Value.class.isAssignableFrom(thisMethod.getDeclaringClass())) {
                Method valueMethod = value.getClass().getMethod(thisMethod.getName(), thisMethod.getParameterTypes());
                return valueMethod.invoke(value, args);
            } else if (PyObject.class.isAssignableFrom(thisMethod.getDeclaringClass())) {
                Method pyObjectMethod = pyObject.getClass().getMethod(thisMethod.getName(), thisMethod.getParameterTypes());
                if (!thisMethod.getName().equals("toString")) {
                    accessed = true;
                }
                return pyObjectMethod.invoke(pyObject, args);
            } else {
                throw new RuntimeException("Failed to invoke PyObjectValue method. Implementing class not found");
            }
        }
    }
}
