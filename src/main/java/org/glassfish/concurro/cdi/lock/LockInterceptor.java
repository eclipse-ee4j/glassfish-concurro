/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.glassfish.concurro.cdi.lock;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.io.Serializable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.glassfish.concurro.cdi.Lock;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;
import static org.glassfish.concurro.cdi.Lock.Type.READ;

@Interceptor
@Lock
@Priority(PLATFORM_BEFORE)
public class LockInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    @AroundInvoke
    public Object doLock(InvocationContext ctx) throws Exception {
        Lock lockAnnotation = ctx.getInterceptorBinding(Lock.class);
        java.util.concurrent.locks.Lock lock = getReadOrWriteLock(lockAnnotation);

        acquireLock(lockAnnotation, lock);

        try {
            return ctx.proceed();
        } finally {
            lock.unlock();
        }
    }

    private java.util.concurrent.locks.Lock getReadOrWriteLock(Lock lockAnnotation) {
        return lockAnnotation.type() == READ? readWriteLock.readLock() : readWriteLock.writeLock();
    }


    private void acquireLock(Lock lockAnnotation, java.util.concurrent.locks.Lock lock) throws InterruptedException {
        switch (lockAnnotation.timeoutType()) {
            case TIMEOUT:
                if (!lock.tryLock(lockAnnotation.accessTimeout(), lockAnnotation.unit())) {
                    throw new IllegalStateException(
                        "Could not obtain lock in " +
                        lockAnnotation.accessTimeout() + " " +
                        lockAnnotation.unit().name());
                }
                break;

            case INDEFINITTE:
                lock.lock();
                break;

            case NOT_PERMITTED:
                if (!lock.tryLock()) {
                    throw new IllegalStateException("Lock already locked, and no wait allowed");
                }
                break;
        }
    }

}