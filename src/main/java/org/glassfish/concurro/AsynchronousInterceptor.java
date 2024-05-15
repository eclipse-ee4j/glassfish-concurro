/*
 * Copyright (c) 2022-2024 Contributors to the Eclipse Foundation
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
package org.glassfish.concurro;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.glassfish.concurro.internal.ManagedCompletableFuture;
import jakarta.annotation.Priority;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.concurrent.CronTrigger;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Schedule;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.glassfish.concurro.internal.AsynchronousScheduledAction;
import org.glassfish.concurro.internal.CompoundTrigger;

/**
 * Interceptor for @Asynchronous.
 *
 * @author Petr Aubrecht &lt;aubrecht@asoftware.cz&gt;
 */
@Interceptor
@Asynchronous
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 5)
public class AsynchronousInterceptor {
    static final Logger log = Logger.getLogger(AsynchronousInterceptor.class.getName());

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Method method = context.getMethod();
        Asynchronous asynchAnnotation = method.getAnnotation(Asynchronous.class);

        if (asynchAnnotation.runAt().length > 0) {
            return schedule(context, method, asynchAnnotation);
        } else {
            return executeDirectly(context, method, asynchAnnotation);
        }
    }

    private CompletableFuture<Object> executeDirectly(InvocationContext context, Method method, Asynchronous asynchAnnotation) {
        String executor = asynchAnnotation.executor();
        executor = executor != null ? executor : "java:comp/DefaultManagedExecutorService"; // provide default value if there is none
        log.log(Level.FINE, "AsynchronousInterceptor.intercept around asynchronous method {0}, executor=''{1}''", new Object[]{method, executor});
        ManagedExecutorService mes = lookupMES(ManagedExecutorService.class, executor, method.getName());
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
                    log.log(Level.SEVERE, "Method annotated with @Asynchronous did not call Asynchronous.Result.complete() at its end: {0}", method.toString());
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

    private static <T> T lookupMES(Class<T> cls, String executor, String methodName) throws RejectedExecutionException {
        T mes;
        try {
            Object lookupMes = new InitialContext().lookup(executor);
            if (lookupMes == null) {
                throw new RejectedExecutionException("ManagedExecutorService with jndi '" + executor + "' not found!");
            }
            if (!(cls.isInstance(lookupMes))) {
                throw new RejectedExecutionException("ManagedExecutorService with jndi '" + executor + "' must be of type " + cls + ", found " + lookupMes.getClass().getName());
            }
            mes = (T) lookupMes;
        } catch (NamingException ex) {
            throw new RejectedExecutionException("ManagedExecutorService with jndi '" + executor + "' not found as requested by asynchronous method " + methodName);
        }
        return mes;
    }

    public CompletableFuture<Object> schedule(InvocationContext context, Method method, Asynchronous asynchAnnotation) {

        // FIXME: challenge testScheduledAsynchWithInvalidJNDIName from TCK and then remove this useless block
        // For runAt, executor is not used at all!
        // check existence of executor
        String executor = asynchAnnotation.executor();
        executor = executor != null ? executor : "java:comp/DefaultManagedExecutorService"; // provide default value if there is none
        lookupMES(ManagedExecutorService.class, executor, method.getName());

        List<CronTrigger> triggers = new ArrayList<>();
        for (Schedule schedule : asynchAnnotation.runAt()) {
            // TODO schedule.skipIfLateBy()
            ZoneId zone = schedule.zone().isEmpty() ? ZoneId.systemDefault() : ZoneId.of(schedule.zone());
            CronTrigger trigger;
            if (schedule.cron().isEmpty()) {
                trigger = new CronTrigger(zone)
                        .seconds(schedule.seconds())
                        .minutes(schedule.minutes())
                        .hours(schedule.hours())
                        .daysOfWeek(schedule.daysOfWeek())
                        .daysOfMonth(schedule.daysOfMonth())
                        .months(schedule.months());
            } else {
                trigger = new CronTrigger(schedule.cron(), zone);
            }
            triggers.add(trigger);
            //new ManagedScheduledExecutorServiceImpl(name, managedThreadFactory, 0, validReturnType, 0, 0, TimeUnit.MILLISECONDS, 0, contextService, AbstractManagedExecutorService.RejectPolicy.RETRY_ABORT)
            //ZonedDateTime firstTime = trigger.getNextRunTime(null, ZonedDateTime.now());
        }
        ManagedScheduledExecutorService mses = lookupMES(ManagedScheduledExecutorService.class, "java:comp/DefaultManagedScheduledExecutorService", method.getName());
        CompletableFuture<Object> future = mses.newIncompleteFuture();
        AsynchronousScheduledAction action = new AsynchronousScheduledAction(context, future);
        CompoundTrigger trigger = new CompoundTrigger(mses, triggers, context);
        final ScheduledFuture<?> scheduledFuture = mses.schedule(action, trigger);
        action.setScheduledFuture(scheduledFuture);
        return future;
    }

}
