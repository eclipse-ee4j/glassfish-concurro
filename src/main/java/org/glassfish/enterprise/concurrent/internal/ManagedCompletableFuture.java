/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2022] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.enterprise.concurrent.internal;

import jakarta.enterprise.concurrent.ManagedExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Managed CompletableFuture, using the provided ManagedExecutorServiceImpl.
 *
 * @author Petr Aubrecht <aubrecht@asoftware.cz>
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
        return super.thenApplyAsync(fn, executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, executor.getContextService().contextualFunction(fn), executor);
    }

    @Override
    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return super.thenCombineAsync(other, fn, executor);
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

    public static <U> CompletableFuture<U> completedFuture(U value, ManagedExecutorService executor) {
        ManagedCompletableFuture<U> future = new ManagedCompletableFuture<>(executor);
        future.complete(value);
        return future;
    }

    public static <U> CompletionStage<U> completedStage(U value, ManagedExecutorService executor) {
        ManagedCompletableFuture<U> future = new ManagedCompletableFuture<>(executor);
        future.complete(value);
        return (CompletionStage<U>) future;
    }

    public static <T> CompletableFuture<T> copy(CompletableFuture<T> future, ManagedExecutorService executor) {
        return future.copy();
    }

    public static <T> CompletionStage<T> copy(CompletionStage<T> stage, ManagedExecutorService executor) {
        return stage.thenApply(Function.identity());
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
