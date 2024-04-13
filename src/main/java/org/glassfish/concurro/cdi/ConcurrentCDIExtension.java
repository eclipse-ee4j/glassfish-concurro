/*
 * Copyright (c) 2022-2024 Payara Foundation and/or its affiliates.
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
import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.transaction.Transactional;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.glassfish.concurro.AsynchronousInterceptor;
import org.glassfish.concurro.internal.ConcurrencyManagedCDIBeans;

/**
 * CDI Extension for Jakarta Concurrent implementation backported from Payara.
 *
 * @author Petr Aubrecht (Payara)
 */
public class ConcurrentCDIExtension implements Extension {

    private static final Logger log = Logger.getLogger(ConcurrentCDIExtension.class.getName());

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery, BeanManager beanManager) {
        log.finest("ConcurrentCDIExtension.beforeBeanDiscovery");
        // Add each of the Concurrent interceptors
        beforeBeanDiscovery.addInterceptorBinding(Asynchronous.class);
        AnnotatedType<AsynchronousInterceptor> asynchronousInterceptor
                = beanManager.createAnnotatedType(AsynchronousInterceptor.class);
        beforeBeanDiscovery.addAnnotatedType(asynchronousInterceptor, AsynchronousInterceptor.class.getName());
    }

    public <T> void processAnnotatedType(@Observes @WithAnnotations({Asynchronous.class}) ProcessAnnotatedType<T> processAnnotatedType,
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

    /**
     * 1: BeforeBeanDiscovery.
     *
     * @param event
     */
    public void beforeBeanDiscovery2(@Observes final BeforeBeanDiscovery event) {
        log.severe("addScope");
    }

    /**
     * 2: ProcessAnnotatedType.
     *
     * @param <T>
     * @param processAnnotatedType
     * @param beanManager
     * @throws Exception
     */
    public <T> void processAnyAnnotatedType(@Observes ProcessAnnotatedType<T> processAnnotatedType,
            BeanManager beanManager) throws Exception {
        log.severe("I'm here, class: " + processAnnotatedType.getClass());
        if (processAnnotatedType.getClass().getName().startsWith("ee.")) {
            log.severe("HERE!!!");
        }
    }

    /**
     * 3: ProcessInjectionTarget.
     *
     * @param <T>
     * @param pit
     */
    public <T> void processInjectionTarget(@Observes ProcessInjectionTarget<T> pit) {
        if (pit.getAnnotatedType().getJavaClass().getName().startsWith("ee.")) {
            // TOOD create contextual proxy
            log.severe("processInjectionTarget " + pit.getInjectionTarget());
        }
    }

    /**
     * 4: ProcessProducer.
     *
     * @param <T>
     * @param <X>
     * @param event
     */
    public <T, X> void processProducer(@Observes ProcessProducer<T, X> event) {
        log.severe("processProducer, " + event.getProducer());
    }

    /**
     * 5: AfterBeanDiscovery.
     *
     * @param event
     */
    void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, BeanManager beanManager) {
        try {
            log.severe("afterBeanDiscovery");

            // define default beans
            event.addBean()
                    .beanClass(ContextService.class)
                    .types(ContextService.class)
                    .scope(ApplicationScoped.class)
                    .addQualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .produceWith((Instance<Object> inst) -> createInstanceContextService(inst, "java:comp/DefaultContextService"));
            event.addBean()
                    .beanClass(ManagedThreadFactory.class)
                    .types(ManagedThreadFactory.class)
                    .scope(ApplicationScoped.class)
                    .addQualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .produceWith((Instance<Object> inst) -> createInstanceContextService(inst, "java:comp/DefaultManagedThreadFactory"));
            event.addBean()
                    .beanClass(ManagedExecutorService.class)
                    .types(ManagedExecutorService.class)
                    .scope(ApplicationScoped.class)
                    .addQualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .produceWith((Instance<Object> inst) -> createInstanceContextService(inst, "java:comp/DefaultManagedExecutorService"));
            event.addBean()
                    .beanClass(ManagedScheduledExecutorService.class)
                    .types(ManagedScheduledExecutorService.class)
                    .scope(ApplicationScoped.class)
                    .addQualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .produceWith((Instance<Object> inst) -> createInstanceContextService(inst, "java:comp/DefaultManagedScheduledExecutorService"));

            // pick up ConcurrencyManagedCDIBeans definitions from JNDI
            InitialContext ctx = new InitialContext();
            ConcurrencyManagedCDIBeans configs = (ConcurrencyManagedCDIBeans) ctx.lookup(ConcurrencyManagedCDIBeans.JDNI_NAME);

            for (ConcurrencyManagedCDIBeans.ConfiguredCDIBean beanDefinition : configs.getBeans()) {
                String jndiName = beanDefinition.jndiName();
                Set<Annotation> annotations = new HashSet<>();
                Set<String> classNames = beanDefinition.qualifiers();
                for (String className : classNames) {
                    Class<? extends Annotation> annoCls = Thread.currentThread().getContextClassLoader().loadClass(className).asSubclass(Annotation.class);
                    Annotation annotationProxy = Annotation.class.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                            new Class<?>[]{Annotation.class, annoCls},
                            new QualifierAnnotationProxy(annoCls)));
                    annotations.add(annotationProxy);
                }
                
                Class<?> beanClass = switch (beanDefinition.definitionType()) {
                    case CONTEXT_SERVICE ->
                        ContextService.class;
                    case MANAGED_THREAD_FACTORY ->
                        ManagedThreadFactory.class;
                    case MANAGED_EXECUTOR_SERVICE ->
                        ManagedExecutorService.class;
                    case MANAGED_SCHEDULED_EXECUTOR_SERVICE ->
                        ManagedScheduledExecutorService.class;
                };

                // register bean
                event.addBean()
                        .beanClass(beanClass)
                        .types(beanClass)
                        .scope(ApplicationScoped.class)
                        .addQualifiers(annotations)
                        .produceWith((Instance<Object> inst) -> createInstanceContextService(inst, jndiName));
            }
        } catch (NamingException ex) {
            log.log(Level.SEVERE, "Unable to load '" + ConcurrencyManagedCDIBeans.JDNI_NAME + "' from JNDI! " + ex.getMessage(), ex);
        } catch (ClassNotFoundException ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * 6. AfterDeploymentValidation.
     *
     * @param event
     * @param beanManager
     */
    public void afterDeploymentValidation(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        log.severe("afterDeploymentValidation");
    }

    /**
     * 7: BeforeShutdown.
     *
     * @param event
     * @param beanManager
     */
    public void beforeShutdown(@Observes BeforeShutdown event, BeanManager beanManager) {
        log.severe("beforeShutdown");
    }

// This is not working as the annotation doesn't need to be on CDI bean
//    public <T> void processAnnotatedType(@Observes @WithAnnotations(ContextServiceDefinition.class) ProcessAnnotatedType<T> pat) {
//    }
//    
    private Object createInstanceContextService(Instance<Object> inst, String jndi) {
        try {
            InitialContext ctx = new InitialContext();
            Object concurrencyObject = ctx.lookup(jndi);
            return concurrencyObject;
        } catch (NamingException ex) {
            Logger.getLogger(ConcurrentCDIExtension.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Unable to fine JNDI '" + jndi + "': " + ex.getMessage(), ex);
        }
    }

}
