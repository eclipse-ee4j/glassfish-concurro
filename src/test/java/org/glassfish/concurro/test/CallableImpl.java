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

package org.glassfish.concurro.test;

import java.util.concurrent.Callable;

public class CallableImpl<T> extends TaskRunEnvironmentTracker implements Callable<T> {

    final T result;
    boolean throwsException;

    public CallableImpl(T result) {
        super(null);
        this.result = result;
    }
    
    public CallableImpl(T result, ManagedTaskListenerImpl taskListener) {
        super(taskListener);
        this.result = result;
    }

    public void setThrowsException(boolean throwsException) {
        this.throwsException = throwsException;        
    }
    
    @Override
    public T call() throws Exception {
        captureThreadContexts();
        if (throwsException) {
            throw new Exception(result.toString());
        }
        return result;
    }
    
}
