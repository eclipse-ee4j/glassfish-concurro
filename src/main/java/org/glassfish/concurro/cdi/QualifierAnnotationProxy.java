/*
 * Copyright (c) 2024 Payara Foundation and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package org.glassfish.concurro.cdi;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import java.lang.reflect.InvocationHandler;

/**
 * Proxy that serves as an instance of a qualifier annotation.
 *
 * FIXME: compare only by class name, do not compare values (only classname is
 * used for specification)
 *
 * Adapted from Nathan Rauh's QualifierProxy.
 */
public class QualifierAnnotationProxy implements InvocationHandler {

    /**
     * The hash code value for the qualifier annotation.
     */
    private final int hashCode;

    /**
     * Accessor methods for annotation fields.
     */
    private final List<Method> methods;

    /**
     * Qualifier annotation class.
     */
    private final Class<?> qualifierClass;

    /**
     * The toString value for the qualifier annotation.
     */
    private final String stringValue;

    /**
     * Create a invocation handler for the specified qualifier annotation class.
     *
     * @param qualifierClass qualifier annotation class.
     */
    public QualifierAnnotationProxy(Class<?> qualifierClass) {
        this.qualifierClass = qualifierClass;
        String qualifierClassName = qualifierClass.getName();

        methods = new ArrayList<>();
        int hash = qualifierClassName.hashCode();
        StringBuilder stringValueBuf = new StringBuilder()
                .append('@')
                .append(qualifierClassName)
                .append('(');

        boolean first = true;
        for (Method method : qualifierClass.getMethods()) {
            if (method.getParameterCount() == 0) {
                String name = method.getName();
                if (!"annotationType".equals(name)
                        && !"hashCode".equals(name)
                        && !"toString".equals(name)) {
                    methods.add(method);

                    Object value = method.getDefaultValue();

                    if (first) {
                        first = false;
                    } else {
                        stringValueBuf.append(", ");
                    }

                    stringValueBuf.append(name).append('=');

                    int hashIncr;
                    if (value instanceof Object[]) {
                        Object[] array = (Object[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof int[]) {
                        int[] array = (int[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof long[]) {
                        long[] array = (long[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof boolean[]) {
                        boolean[] array = (boolean[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof double[]) {
                        double[] array = (double[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof float[]) {
                        float[] array = (float[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof short[]) {
                        short[] array = (short[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof byte[]) {
                        byte[] array = (byte[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value instanceof char[]) {
                        char[] array = (char[]) value;
                        hashIncr = Arrays.hashCode(array);
                        stringValueBuf.append(Arrays.toString(array));
                    } else if (value != null) {
                        hashIncr = value.hashCode();
                        stringValueBuf.append(value);
                    } else {
                        // value == null
                        hashIncr = "null".hashCode();
                        stringValueBuf.append("null");
                    }

                    // JavaDoc for Annotation requires the hash code to be the sum of
                    // 127 times each member's name xor with the hash code of its value
                    hash += (127 * name.hashCode()) ^ hashIncr;
                }
            }
        }

        stringValue = stringValueBuf.append(")[QualifierProxy]").toString();
        hashCode = hash;
    }

    /**
     * Compare a proxy qualifier with another instance.
     *
     * @param proxy qualifier that is implemented by QualifierProxy.
     * @param other other object that is possibly a matching qualifier instance.
     * @return true if both represent the same qualifier, otherwise false.
     */
    private boolean equals(Object proxy, Object other) throws Exception {
        boolean equal;
        if (proxy == other) {
            equal = true;
        } else if (qualifierClass.isInstance(other)) {
            boolean isProxy = Proxy.isProxyClass(other.getClass());
            InvocationHandler otherHandler = isProxy ? Proxy.getInvocationHandler(other) : null;
            if (otherHandler instanceof QualifierAnnotationProxy) {
                equal = ((QualifierAnnotationProxy) otherHandler).qualifierClass.equals(qualifierClass);
            } else {
                // The other instance is not a QualifierProxy, but it might still match.
                // For a proper comparison, meeting the requirements of the JavaDoc for Annotation.equals
                // we need to invoke all methods (except for annotationType/equals/hashCode/toString)
                // on both instances and compare the values.
                equal = true;
                for (Method method : methods) {
                    Object value1 = method.getDefaultValue();
                    Object value2 = method.invoke(other);

                    equal = value1 instanceof Object[] ? Arrays.equals((Object[]) value1, (Object[]) value2)
                            : value1 instanceof int[] ? Arrays.equals((int[]) value1, (int[]) value2)
                                    : value1 instanceof long[] ? Arrays.equals((long[]) value1, (long[]) value2)
                                            : value1 instanceof boolean[] ? Arrays.equals((boolean[]) value1, (boolean[]) value2)
                                                    : value1 instanceof double[] ? Arrays.equals((double[]) value1, (double[]) value2)
                                                            : value1 instanceof float[] ? Arrays.equals((float[]) value1, (float[]) value2)
                                                                    : value1 instanceof short[] ? Arrays.equals((short[]) value1, (short[]) value2)
                                                                            : value1 instanceof byte[] ? Arrays.equals((byte[]) value1, (byte[]) value2)
                                                                                    : value1 instanceof char[] ? Arrays.equals((char[]) value1, (char[]) value2)
                                                                                            : Objects.equals(value1, value2);

                    if (!equal) {
                        break;
                    }
                }
            }
        } else {
            equal = false;
        }

        return equal;
    }

    /**
     * Implements the 4 methods of java.lang.Annotation: hashCode(), toString(),
     * equals(other), and annotationType().
     *
     * Handles annotation fields with default values by delegating to the
     * default value.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        int numParams = method.getParameterCount();

        if (numParams == 0 && "hashCode".equals(methodName)) {
            return hashCode;
        } else if (numParams == 0 && "toString".equals(methodName)) {
            return stringValue;
        } else if (numParams == 0 && "annotationType".equals(methodName)) {
            return qualifierClass;
        } else if (numParams == 1 && "equals".equals(methodName)) {
            return equals(proxy, args[0]);
        } else {
            return method.getDefaultValue();
        }
    }
}
