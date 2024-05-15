/*
 * Copyright (c) 2024 Payara Foundation and/or its affiliates.
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
package org.glassfish.concurro.internal;

import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.interceptor.InvocationContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

/**
 * Runnable executed by AsynchronoutIntercepter as the scheduled action.
 *
 * @author Petr Aubrecht
 */
public class AsynchronousScheduledAction implements Runnable {

    private final InvocationContext context;
    private final CompletableFuture<Object> future;
    private ScheduledFuture<?> scheduledFuture;

    public AsynchronousScheduledAction(InvocationContext context, CompletableFuture<Object> future) {
        this.context = context;
        this.future = future;
    }

    @Override
    public void run() {
        Asynchronous.Result.setFuture(future);
        boolean cancelScheduler = false;
        try {
            CompletableFuture<Object> returnedFuture = (CompletableFuture<Object>) context.proceed();
            if (returnedFuture != null && scheduledFuture != null) {
                cancelScheduler = true;
            }
        } catch (Exception e) {
            //throw new IllegalStateException("Invocation context proceed failed!", e);
            future.completeExceptionally(e);
        } finally {
            Asynchronous.Result.setFuture(null);
            if (future.isDone() || future.isCompletedExceptionally() || future.isCancelled()) {
                cancelScheduler = true;
            }
        }
        if (cancelScheduler) {
            scheduledFuture.cancel(false);
        }
    }

    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }

}
