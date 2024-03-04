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

package org.glassfish.concurro.spi;

import java.io.Serializable;
import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.concurrent.ManagedTask;

/**
 * To be implemented by application server for performing proper transaction
 * setup before invoking a proxy method of a contextual proxy object created by 
 * various {@code createContextualProxy} methods in {@link ContextService} 
 * and after the proxy method has finished running.
 */
public interface TransactionSetupProvider extends Serializable {

    /**
     * Method to be called before invoking the proxy method to allow the
     * Java EE Product Provider to perform any transaction-related setup.
     * 
     * @param transactionExecutionProperty The value of the {@link ManagedTask#TRANSACTION}
     *          execution property for the ContextService that creates the
     *          contextual proxy object.
     * @return A TransactionHandle that will be passed back to the 
     *         {@link #afterProxyMethod(org.glassfish.concurro.spi.TransactionHandle, java.lang.String) }
     *         after the proxy method returns.
     */
    public TransactionHandle beforeProxyMethod(String transactionExecutionProperty);
    

    /**
     * Method to be called after invoking the proxy method to allow the 
     * Java EE Product Provider to perform any transaction-related cleanup.
     * 
     * @param handle The TransactionHandle that was returned in the 
     *               {@link #beforeProxyMethod(java.lang.String) } call.
     * @param transactionExecutionProperty The value of the {@link ManagedTask#TRANSACTION}
     *          execution property for the ContextService that creates the
     *          contextual proxy object.
     */
    public void afterProxyMethod(TransactionHandle handle, String transactionExecutionProperty);
}
