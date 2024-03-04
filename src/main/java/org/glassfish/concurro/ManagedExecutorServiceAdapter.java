/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation.
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Payara Foundation and/or its affiliates.
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

package org.glassfish.concurro;

import jakarta.enterprise.concurrent.ContextService;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The ManagedExecutorService instance to be handed to the
 * application components, with all life cycle operations disabled.
 */
public class ManagedExecutorServiceAdapter extends AbstractManagedExecutorServiceAdapter
implements ManagedExecutorService {

    protected AbstractManagedExecutorService executor;

    public ManagedExecutorServiceAdapter(AbstractManagedExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return executor.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    @Override
    public <U> CompletableFuture<U> completedFuture(U value) {
        return executor.completedFuture(value);
    }

    @Override
    public <U> CompletionStage<U> completedStage(U value) {
        return executor.completedStage(value);
    }

    @Override
    public <T> CompletableFuture<T> copy(CompletableFuture<T> future) {
        return executor.copy(future);
    }

    @Override
    public <T> CompletionStage<T> copy(CompletionStage<T> completionStage) {
        return executor.copy(completionStage);
    }

    @Override
    public <U> CompletableFuture<U> failedFuture(Throwable ex) {
        return executor.failedFuture(ex);
    }

    @Override
    public <U> CompletionStage<U> failedStage(Throwable ex) {
        return executor.failedStage(ex);
    }

    @Override
    public ContextService getContextService() {
        return executor.getContextService();
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return executor.newIncompleteFuture();
    }

    @Override
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return executor.runAsync(runnable);
    }

    @Override
    public <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return executor.supplyAsync(supplier);
    }

}
