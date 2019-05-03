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

package org.glassfish.enterprise.concurrent.spi;

import java.io.Serializable;
import java.util.Map;
import jakarta.concurrency.ContextService;

/**
 * To be implemented by application server for setting up proper execution
 * context before running a task, and also for resetting the execution context
 * after running a task.
 */
public interface ContextSetupProvider extends Serializable {
    
    /**
     * Called by ManagedExecutorService in the same thread that submits a
     * task to save the execution context of the submitting thread. 
     * 
     * @param contextService ContextService containing information on what
     * context should be saved
     * 
     * @return A ContextHandle that will be passed to the setup method
     * in the thread executing the task
     */
    public ContextHandle saveContext(ContextService contextService);
    
    /**
     * Called by ManagedExecutorService in the same thread that submits a
     * task to save the execution context of the submitting thread. 
     * 
     * @param contextService ContextService containing information on what
     * context should be saved
     * @param contextObjectProperties Additional properties specified for
     * for a context object when the ContextService object was created.
     * 
     * @return A ContextHandle that will be passed to the setup method
     * in the thread executing the task
     */
    public ContextHandle saveContext(ContextService contextService,
            Map<String, String> contextObjectProperties);
    
    /**
     * Called by ManagedExecutorService before executing a task to set up thread
     * context. It will be called in the thread that will be used for executing
     * the task.
     * 
     * @param contextHandle The ContextHandle object obtained from the call
     * to #saveContext
     * 
     * @return A ContextHandle that will be passed to the reset method
     * in the thread executing the task
     * 
     * @throws IllegalStateException if the ContextHandle is no longer valid.
     * For example, the application component that the ContextHandle was 
     * created for is no longer running or is undeployed.
     */
    public ContextHandle setup(ContextHandle contextHandle) throws IllegalStateException;
    
    /**
     * Called by ManagedExecutorService after executing a task to clean up and
     * reset thread context. It will be called in the thread that was used
     * for executing the task.
     * 
     * @param contextHandle The ContextHandle object obtained from the call
     * to #setup
     */
    public void reset(ContextHandle contextHandle);
    
}
