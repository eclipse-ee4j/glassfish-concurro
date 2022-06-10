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
package org.glassfish.enterprise.concurrent.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.concurrent.ManagedExecutorService;

/**
 * Managed CompletableFuture, using the provided ManagedExecutorServiceImpl.
 *
 * @author Petr Aubrecht &lt;aubrecht@asoftware.cz&gt;
 */
public class ManagedCompletableFuture<T> extends CompletableFuture<T> {

    private final ManagedExecutorService executor;

    public ManagedCompletableFuture(ManagedExecutorService executor) {
        this.executor = executor;
    }

    // default executor is our executor
    @Override
    public Executor defaultExecutor() {
        return executor;
    }

    // this method is used to create a new CompletableFuture, provide the managed one
    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new ManagedCompletableFuture<>(executor);
    }

    @Override
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return super.thenApply(executor.getContextService().contextualFunction(fn));
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, ManagedExecutorService executor) {
        ManagedCompletableFuture<T> managedFuture = new ManagedCompletableFuture<>(executor);
        executor.execute(() -> {
            try {
                managedFuture.complete(supplier.get());
            } catch (Exception e) {
                managedFuture.completeExceptionally(e);
            }
        });
        return managedFuture;
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApplyAsync(fn, executor);
    }

    @Override
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return super.thenApplyAsync(this.executor.getContextService().contextualFunction(fn), executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, executor.getContextService().contextualFunction(fn), executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return super.thenCombineAsync(other, this.executor.getContextService().contextualFunction(fn), executor);
    }

    @Override
    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        // FIXME: implement correctly
        return super.handleAsync(executor.getContextService().contextualFunction(fn));
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return super.thenCombine(other, executor.getContextService().contextualFunction(fn));
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, executor);
    }

    @Override
    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return super.applyToEitherAsync(other, this.executor.getContextService().contextualFunction(fn), executor); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return super.thenAcceptBoth(other, executor.getContextService().contextualConsumer(action));
    }

    @Override
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return super.exceptionally(this.executor.getContextService().contextualFunction(fn));
    }

    public static <U> CompletableFuture<U> completedFuture(U value, ManagedExecutorService executor) {
        ManagedCompletableFuture<U> future = new ManagedCompletableFuture<>(executor);
        future.complete(value);
        return future;
    }

    public static <U> CompletionStage<U> completedStage(U value, ManagedExecutorService executor) {
        ManagedCompletableFuture<U> future = new ManagedCompletableFuture<>(executor);
        future.complete(value);
        return future;
    }

    public static <U> CompletableFuture<U> failedFuture(Throwable ex, ManagedExecutorService executor) {
        return executor.failedFuture(ex);
    }

    public static <U> CompletionStage<U> failedStage(Throwable ex, ManagedExecutorService executor) {
        return executor.failedStage(ex);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable, ManagedExecutorService executor) {
        return CompletableFuture.runAsync(runnable, executor);
    }

}
