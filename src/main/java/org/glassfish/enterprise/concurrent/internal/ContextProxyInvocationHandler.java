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

package org.glassfish.enterprise.concurrent.internal;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.concurrent.ManagedTask;
import java.lang.reflect.InvocationTargetException;
import org.glassfish.enterprise.concurrent.ContextServiceImpl;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.spi.TransactionHandle;
import org.glassfish.enterprise.concurrent.spi.TransactionSetupProvider;

/**
 * InvocationHandler used by ContextServiceImpl
 */
public class ContextProxyInvocationHandler implements InvocationHandler, Serializable {

    static final long serialVersionUID = -2887560418884002777L;
    
    final protected ContextSetupProvider contextSetupProvider;
    protected ContextService contextService;
    final protected ContextHandle capturedContextHandle;
    final protected TransactionSetupProvider transactionSetupProvider;
    final protected Object proxiedObject;
    protected Map<String, String> executionProperties;
   
    public ContextProxyInvocationHandler(ContextServiceImpl contextService, Object proxiedObject, 
            Map<String, String> executionProperties) {
        this.contextSetupProvider = contextService.getContextSetupProvider();
        this.proxiedObject = proxiedObject;
        this.contextService = contextService;
        this.transactionSetupProvider = contextService.getTransactionSetupProvider();
        this.executionProperties = executionProperties;
        this.capturedContextHandle = 
                contextSetupProvider.saveContext(contextService, executionProperties);
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = null;
        Class methodDeclaringClass = method.getDeclaringClass();
        
        if (methodDeclaringClass == java.lang.Object.class) {
            // hashCode, equals, or toString method of java.lang.Object will
            // have java.lang.Object as declaring class (per java doc in
            // java.lang.reflect.Proxy). These methods would not be run
            // under creator's context
            result = method.invoke(proxiedObject, args);
        }
        else {
            // Ask TransactionSetupProvider to perform any transaction related
            // setup before running the proxy. For example, suspend current
            // transaction on current thread unless TRANSACTION property is set
            // to USE_TRANSACTION_OF_EXECUTION_THREAD
            // Do it before contextSetupProvider.setup, as it can clear transaction.
            TransactionHandle txHandle = null;
            if (transactionSetupProvider != null) {
                txHandle = transactionSetupProvider.beforeProxyMethod(getTransactionExecutionProperty());
            }
            // for all other methods, invoke under creator's context
            ContextHandle contextHandleForReset = contextSetupProvider.setup(capturedContextHandle);
            try {
                result = method.invoke(proxiedObject, args);
            } catch(InvocationTargetException e) {
                // rethrow the original exception, not InvocationTargetException
                throw e.getCause();
            } finally {
                contextSetupProvider.reset(contextHandleForReset);
                if (transactionSetupProvider != null) {
                    transactionSetupProvider.afterProxyMethod(txHandle, getTransactionExecutionProperty());
                }
            }
        }
        return result;
    }

    public Map<String, String> getExecutionProperties() {
        // returns a copy of the executionProperties
        if (executionProperties == null) {
            return null;
        }
        Map<String, String> copy = new HashMap<>();
        copy.putAll(executionProperties);
        return copy;
    }

    public ContextService getContextService() {
        return contextService;
    }
    
    protected String getTransactionExecutionProperty() {
      if (executionProperties != null && executionProperties.get(ManagedTask.TRANSACTION) != null) {
          return executionProperties.get(ManagedTask.TRANSACTION);
      }
      return ManagedTask.SUSPEND;
    }
    
}
