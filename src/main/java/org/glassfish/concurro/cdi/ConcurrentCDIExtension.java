/*
 * Copyright (c) 2022 Payara Foundation and/or its affiliates.
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

import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.transaction.Transactional;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import org.glassfish.concurro.AsynchronousInterceptor;

/**
 * CDI Extension for Jakarta Concurrent implementation backported from Payara.
 *
 * @author Petr Aubrecht (Payara)
 */
public class ConcurrentCDIExtension implements Extension {

    private static final Logger log = Logger.getLogger(ConcurrentCDIExtension.class.getName());

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery, BeanManager beanManager) {
        log.finest("ConcurrentCDIExtension.beforeBeanDiscovery");
        // Add each of the Concurrent interceptors
        beforeBeanDiscovery.addInterceptorBinding(Asynchronous.class);
        AnnotatedType<AsynchronousInterceptor> asynchronousInterceptor
                = beanManager.createAnnotatedType(AsynchronousInterceptor.class);
        beforeBeanDiscovery.addAnnotatedType(asynchronousInterceptor, AsynchronousInterceptor.class.getName());
    }

    <T> void processAnnotatedType(@Observes @WithAnnotations({Asynchronous.class}) ProcessAnnotatedType<T> processAnnotatedType,
            BeanManager beanManager) throws Exception {
        log.finest("ConcurrentCDIExtension.processAnnotatedType");
        AnnotatedType<T> annotatedType = processAnnotatedType.getAnnotatedType();

        // Validate the Asynchronous annotations for each annotated method
        Set<AnnotatedMethod<? super T>> annotatedMethods = annotatedType.getMethods();
        for (AnnotatedMethod<?> annotatedMethod : annotatedMethods) {
            Method method = annotatedMethod.getJavaMember();
            if (method.getDeclaringClass().equals(AsynchronousInterceptor.class)) {
                // skip interceptor
                continue;
            }
            Asynchronous annotation = method.getAnnotation(Asynchronous.class);
            if (annotation == null) {
                // method in the class, which is NOT annotated @Asynchronous
                continue;
            }
            Class<?> returnType = method.getReturnType();
            boolean validReturnType = returnType.equals(Void.TYPE)
                    || returnType.equals(CompletableFuture.class)
                    || returnType.equals(CompletionStage.class);
            if (!validReturnType) {
                throw new UnsupportedOperationException("Method \"" + method.getName() + "\""
                        + " annotated with " + Asynchronous.class.getCanonicalName() + " does not return a CompletableFuture, CompletableFuture or void.");
            }
            Transactional transactionalAnnotation = annotatedMethod.getAnnotation(Transactional.class);
            if (transactionalAnnotation != null
                    && transactionalAnnotation.value() != Transactional.TxType.REQUIRES_NEW
                    && transactionalAnnotation.value() != Transactional.TxType.NOT_SUPPORTED) {
                throw new UnsupportedOperationException("Method \"" + method.getName() + "\""
                        + " annotated with " + Asynchronous.class.getCanonicalName() + " is annotated with @Transactional, but not one of the allowed types: REQUIRES_NEW or NOT_SUPPORTED.");
            }
        }
    }
}
