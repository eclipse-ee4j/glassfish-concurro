/*
 * Copyright (c) 2024 Payara Foundation and/or its affiliates.
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import jakarta.enterprise.concurrent.CronTrigger;
import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Trigger;
import jakarta.interceptor.InvocationContext;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

/**
 * Trigger based on a list of triggers, always plans the closes next time from
 * all triggers.
 *
 * @author Petr Aubrecht
 */
public class CompoundTrigger implements Trigger {

    private final ManagedScheduledExecutorService mses;
    private final List<CronTrigger> triggers;
    private final InvocationContext context;

    public CompoundTrigger(ManagedScheduledExecutorService mses, List<CronTrigger> triggers, InvocationContext context) {
        this.mses = mses;
        this.triggers = triggers;
        this.context = context;
    }

    @Override
    public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = null;
        for (CronTrigger trigger : triggers) {
            ZonedDateTime nextTime = trigger.getNextRunTime(lastExecutionInfo, now);
            if (next == null || next.isAfter(nextTime)) {
                next = nextTime;
            }
        }
        return next == null ? null : Date.from(next.toInstant());
    }

    @Override
    public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
        // TODO
        return false;
    }
}
