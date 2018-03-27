/*
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.enterprise.concurrent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.enterprise.concurrent.ManagedTask;
import org.glassfish.enterprise.concurrent.test.ClassloaderContextSetupProvider;
import org.glassfish.enterprise.concurrent.test.DummyTransactionSetupProvider;
import org.glassfish.enterprise.concurrent.test.ManagedTaskListenerImpl;
import org.glassfish.enterprise.concurrent.test.NamedClassLoader;
import org.glassfish.enterprise.concurrent.test.RunnableImpl;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class ContextServiceImplTest {
    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testCreateContextualProxy() throws Exception {
        final String classloaderName = "testCreateContextualProxy";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        DummyTransactionSetupProvider txSetupProvider = new DummyTransactionSetupProvider();
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = 
                new ContextServiceImpl("myContextService", contextSetupProvider, txSetupProvider);
        Runnable proxy = contextService.createContextualProxy(task, Runnable.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Run on same thread
        proxy.run();

        task.verifyAfterRun(classloaderName);
        assertNull(contextSetupProvider.contextServiceProperties);

        verifyTransactionSetupProvider(txSetupProvider, ManagedTask.SUSPEND);
        // did we revert the classloader back to the original one?
        assertEquals(original, Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void testCreateContextualProxy_multiple_interfaces() throws Exception {
        final String classloaderName = "testCreateContextualProxy_multiple_interfaces";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        ComparableRunnableImpl task = new ComparableRunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        
        // we can cast the proxy to any of the 2 interfaces
        Object proxy = contextService.createContextualProxy(task, Runnable.class, Comparable.class);
        Comparable comparableProxy = (Comparable) contextService.createContextualProxy(task, Runnable.class, Comparable.class);
        ComparableRunnable comparableRunnableProxy = (ComparableRunnable) contextService.createContextualProxy(task, ComparableRunnable.class);
                
        // we cannot cast to ComparableRunnable
        try {
            ComparableRunnable proxy1 = (ComparableRunnable) contextService.createContextualProxy(task, Runnable.class, Comparable.class);
            fail("expected exception not found");
        } catch (ClassCastException expected) {
            // expected
        }
        // we cannot cast to ComparableRunnableImpl
        try {
            ComparableRunnableImpl proxy1 = (ComparableRunnableImpl) contextService.createContextualProxy(task, Runnable.class, Comparable.class);
            fail("expected exception not found");
        } catch (ClassCastException expected) {
            // expected
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Use proxy as Runnable to run on same thread
        ((Runnable)proxy).run();
        
        // Can also use proxy as Comparable
        Comparable compProxy = (Comparable)proxy;

        task.verifyAfterRun(classloaderName);
        assertNull(contextSetupProvider.contextServiceProperties);

        // did we revert the classloader back to the original one?
        assertEquals(original, Thread.currentThread().getContextClassLoader());
    }


    @Test
    public void testCreateContextualProxy_withProperties() throws Exception {
        final String classloaderName = "testCreateContextualProxy_withProperties";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        DummyTransactionSetupProvider txSetupProvider = new DummyTransactionSetupProvider();
        Map<String, String> props = new HashMap<>();
        props.put("custom", "true");
        props.put(ManagedTask.TRANSACTION, ManagedTask.USE_TRANSACTION_OF_EXECUTION_THREAD);
        ComparableRunnableImpl task = new ComparableRunnableImpl(null);
        ContextServiceImpl contextService = 
                new ContextServiceImpl("myContextService", contextSetupProvider, txSetupProvider);
        Runnable proxy = (Runnable) contextService.createContextualProxy(task, props, Runnable.class, Comparable.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Run on same thread
        proxy.run();

        task.verifyAfterRun(classloaderName);
        assertEquals("true", contextSetupProvider.contextServiceProperties.get("custom"));

        verifyTransactionSetupProvider(txSetupProvider, ManagedTask.USE_TRANSACTION_OF_EXECUTION_THREAD);
        // did we revert the classloader back to the original one?
        assertEquals(original, Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void testCreateContextualProxy_withProperties_multiple_interfaces() throws Exception {
        final String classloaderName = "testCreateContextualProxy_withProperties";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        Map<String, String> props = new HashMap<>();
        props.put("custom", "false");
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        Runnable proxy = contextService.createContextualProxy(task, props, Runnable.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Run on same thread
        proxy.run();

        task.verifyAfterRun(classloaderName);
        assertEquals("false", contextSetupProvider.contextServiceProperties.get("custom"));

        // did we revert the classloader back to the original one?
        assertEquals(original, Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void testCreateContextualProxy_wrongInterface() throws Exception {
        final String classloaderName = "testCreateContextualProxy_wrongInterface";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        try {
            // RunnableImpl does not implements Callable
            Object proxy = contextService.createContextualProxy(task, Callable.class, Runnable.class);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException expected) {
            // expected exception
        }
    }

    @Test
    public void testCreateContextualProxy_withProperties_wrongInterface() throws Exception {
        final String classloaderName = "testCreateContextualProxy_withProperties_wrongInterface";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        Map<String, String> props = new HashMap<>();
        props.put("custom", "false");
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        try {
            // RunnableImpl does not implements Callable
            Object proxy = contextService.createContextualProxy(task, props, Callable.class, Runnable.class);
            fail("expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException expected) {
            // expected exception
        }
    }

    @Test
    public void testCreateContextualProxy_serializable() throws Exception {
        final String classloaderName = "testCreateContextualProxy_serializable";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        DummyTransactionSetupProvider txSetupProvider = new DummyTransactionSetupProvider();
        SerializableCallable task = new SerializableCallable();
        ContextServiceImpl contextService = 
                new ContextServiceImpl("myContextService", contextSetupProvider, txSetupProvider);
        Callable<String> proxy = contextService.createContextualProxy(task, Callable.class);

        assertTrue(proxy instanceof Serializable);
        
        // verify that the proxy can be serialized
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
            ObjectOutputStream out = new ObjectOutputStream(bos) ;
            out.writeObject(proxy);
            out.close();
            byte[] bytes = bos.toByteArray();
            
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object deserialized = ois.readObject();
            assertTrue(deserialized instanceof Callable);
            assertEquals(classloaderName, ((Callable)deserialized).call());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }


    @Test
    public void testContextualProxy_hashCode() throws Exception {
        final String classloaderName = "testContextualProxy_hashCode";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        Runnable proxy = contextService.createContextualProxy(task, Runnable.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Run on same thread
        assertEquals(task.hashCode(), proxy.hashCode());

        assertEquals(original, task.taskRunClassLoader);
    }

    @Test
    public void testContextualProxy_toString() throws Exception {
        final String classloaderName = "testContextualProxy_toString";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        Runnable proxy = contextService.createContextualProxy(task, Runnable.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Run on same thread
        assertEquals(task.toString(), proxy.toString());

        assertEquals(original, task.taskRunClassLoader);
    }

    @Test
    public void testContextualProxy_equals() throws Exception {
        final String classloaderName = "testContextualProxy_equals";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService", contextSetupProvider);
        Runnable proxy = contextService.createContextualProxy(task, Runnable.class);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        // Run on same thread
        assertEquals(task.equals(task), proxy.equals(task));

        assertEquals(original, task.taskRunClassLoader);
    }

    @Test
    public void testGetExecutionProperties() throws Exception {
        final String classloaderName = "testGetProperties";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        Map<String, String> props = new HashMap<>();
        final String PROP_NAME = "myProp";
        props.put(PROP_NAME, "true");
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService1", contextSetupProvider);
        Runnable proxy = contextService.createContextualProxy(task, props, Runnable.class);
        
        Map<String, String> copy = contextService.getExecutionProperties(proxy);
        assertEquals("true", copy.get(PROP_NAME));
        
        // update the property value in the copy. Should not affect the property value of the proxy object
        copy.put(PROP_NAME, "false");
        
        Map<String, String> copy2 = contextService.getExecutionProperties(proxy);
        assertEquals("true", copy2.get(PROP_NAME));
    }

    @Test
    public void testGetExecutionProperties_invalidProxy() throws Exception {
        final String classloaderName = "testGetProperties_invalidProxy";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService = new ContextServiceImpl("myContextService1", contextSetupProvider);

        try {
            contextService.getExecutionProperties(task);
            fail("expected exception not thrown");
        } catch (IllegalArgumentException ex) {
            // expected exception
        }
    }

    @Test
    public void testGetExecutionProperties_invalidProxy2() throws Exception {
        final String classloaderName = "testGetProperties_invalidProxy2";
        ClassloaderContextSetupProvider contextSetupProvider = new ClassloaderContextSetupProvider(classloaderName);
        RunnableImpl task = new RunnableImpl(null);
        ContextServiceImpl contextService1 = new ContextServiceImpl("myContextService1", contextSetupProvider);
        ContextServiceImpl contextService2 = new ContextServiceImpl("myContextService2", contextSetupProvider);
        Runnable proxy = (Runnable) contextService1.createContextualProxy(task, new Class[]{Runnable.class});

        try {
            contextService2.getExecutionProperties(proxy);
            fail("expected exception not thrown");
        } catch (IllegalArgumentException ex) {
            // expected exception
        }
    }
    
    protected void verifyTransactionSetupProvider(DummyTransactionSetupProvider provider, String transactionExecutionProperty) {
        assertTrue(provider.beforeProxyMethodCalled);
        assertTrue(provider.afterProxyMethodCalled);
        assertTrue(provider.sameTransactionHandle);
        assertEquals(transactionExecutionProperty, provider.transactionExecutionPropertyBefore);
        assertEquals(transactionExecutionProperty, provider.transactionExecutionPropertyAfter);
    }
    
    public static interface ComparableRunnable extends Runnable, Comparable {
        
    }
    
    public static class ComparableRunnableImpl extends RunnableImpl implements ComparableRunnable {

        public ComparableRunnableImpl(ManagedTaskListenerImpl taskListener, RuntimeException runException) {
            super(taskListener, runException);
        }

        public ComparableRunnableImpl(ManagedTaskListenerImpl taskListener) {
            super(taskListener);
        }

        @Override
        public int compareTo(Object o) {
            // we are not really interested in compareTo. We just want to 
            // have a class that implements multiple interfaces
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
    
    public static class SerializableCallable implements Callable<String>, Serializable {

        @Override
        public String call() {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl instanceof NamedClassLoader) {
                return ((NamedClassLoader)cl).getName();
            }
            return cl.toString();
        }
        
    }

}
