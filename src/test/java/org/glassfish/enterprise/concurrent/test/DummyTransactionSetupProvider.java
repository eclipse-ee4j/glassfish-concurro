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

package org.glassfish.enterprise.concurrent.test;

import org.glassfish.enterprise.concurrent.spi.TransactionHandle;
import org.glassfish.enterprise.concurrent.spi.TransactionSetupProvider;


public class DummyTransactionSetupProvider implements TransactionSetupProvider {

    public boolean beforeProxyMethodCalled;
    public boolean afterProxyMethodCalled;
    public String transactionExecutionPropertyBefore;
    public String transactionExecutionPropertyAfter;
    private TransactionHandleImpl transactionHandle;
    public boolean sameTransactionHandle;
    
    public TransactionHandle beforeProxyMethod(String transactionExecutionProperty) {
        beforeProxyMethodCalled = true;
        transactionExecutionPropertyBefore = transactionExecutionProperty;
        transactionHandle = new TransactionHandleImpl(Long.toString(System.currentTimeMillis()));
        return transactionHandle;
    }

    public void afterProxyMethod(TransactionHandle handle, String transactionExecutionProperty) {
        afterProxyMethodCalled = true;
        transactionExecutionPropertyAfter = transactionExecutionProperty;
        sameTransactionHandle = (transactionHandle == handle);
    }
    
    public static class TransactionHandleImpl implements TransactionHandle {
        String token;
        
        TransactionHandleImpl(String token) {
            this.token = token;
        }
    }
}
