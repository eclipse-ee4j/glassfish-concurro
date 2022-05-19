/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import jakarta.annotation.Priority;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.glassfish.enterprise.concurrent.internal.ManagedCompletableFuture;

/**
 * Interceptor for @Asynchronous.
 *
 * @author Petr Aubrecht <aubrecht@asoftware.cz>
 */
@Interceptor
@Asynchronous
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 5)
public class AsynchronousInterceptor {
    static final Logger log = Logger.getLogger(AsynchronousInterceptor.class.getName());

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        String executor = context.getMethod().getAnnotation(Asynchronous.class).executor();
        executor = executor != null ? executor : "java:comp/DefaultManagedExecutorService"; // provide default value if there is none
        log.log(Level.FINE, "AsynchronousInterceptor.intercept around asynchronous method {0}, executor=''{1}''", new Object[]{context.getMethod(), executor});
        ManagedExecutorService mes;
        try {
            Object lookupMes = new InitialContext().lookup(executor);
            if (lookupMes == null) {
                throw new RejectedExecutionException("ManagedExecutorService with jndi '" + executor + "' not found!");
            }
            if (!(lookupMes instanceof ManagedExecutorService)) {
                throw new RejectedExecutionException("ManagedExecutorService with jndi '" + executor + "' must be of type jakarta.enterprise.concurrent.ManagedExecutorService, found " + lookupMes.getClass().getName());
            }
            mes = (ManagedExecutorService) lookupMes;
        } catch (NamingException ex) {
            throw new RejectedExecutionException("ManagedExecutorService with jndi '" + executor + "' not found as requested by asynchronous method " + context.getMethod());
        }
        CompletableFuture<Object> resultFuture = new ManagedCompletableFuture<>(mes);
        mes.submit(() -> {
            Asynchronous.Result.setFuture(resultFuture);
            CompletableFuture<Object> returnedFuture = resultFuture;
            try {
                // the asynchronous method is responsible for calling Asynchronous.Result.complete()
                returnedFuture = (CompletableFuture<Object>) context.proceed();
            } catch (Exception ex) {
                resultFuture.completeExceptionally(ex);
            } finally {
                // Check if Asynchronous.Result is not completed?
                if (!returnedFuture.isDone()) {
                    log.log(Level.SEVERE, "Method annotated with @Asynchronous did not call Asynchronous.Result.complete() at its end: {0}", context.getMethod().toString());
                    Asynchronous.Result.getFuture().cancel(true);
                }
                if (returnedFuture != Asynchronous.Result.getFuture()) {
                    // if the asynchronous methods returns a different future, use this to complete the resultFuture
                    try {
                        resultFuture.complete(returnedFuture.get());
                    } catch (InterruptedException | ExecutionException e) {
                        resultFuture.completeExceptionally(e);
                    }
                }
                // cleanup after asynchronous call
                Asynchronous.Result.setFuture(null);
            }
        });
        return resultFuture;
    }
}
