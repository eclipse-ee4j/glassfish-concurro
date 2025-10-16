/*
 * Copyright (c) 2024, 2025 Contributors to the Eclipse Foundation
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

import jakarta.enterprise.concurrent.CronTrigger;
import jakarta.enterprise.concurrent.LastExecution;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.concurrent.Trigger;

import java.lang.System.Logger;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.System.Logger.Level.TRACE;

/**
 * Trigger based on a list of triggers, always plans the closes next time from
 * all triggers.
 *
 * @author Petr Aubrecht
 */
public class CompoundTrigger implements Trigger {
    private static final Logger LOG = System.getLogger(CompoundTrigger.class.getName());

    private final ManagedScheduledExecutorService mses;
    private final List<LateAwareTrigger> triggers = new ArrayList<>();
    private long secondsLate;
    private ZonedDateTime nextField = null;

    public CompoundTrigger(ManagedScheduledExecutorService mses) {
        this.mses = mses;
    }

    @Override
    public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = null;
        for (LateAwareTrigger trigger : triggers) {
            ZonedDateTime nextTime = trigger.trigger().getNextRunTime(lastExecutionInfo, now);
            if (next == null || next.isAfter(nextTime)) {
                next = nextTime;
                secondsLate = trigger.skipIfLateBySeconds();
                nextField = next;
            }
        }
        return next == null ? null : Date.from(next.toInstant());
    }

    @Override
    public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
        ZonedDateTime scheduledRunTimeJT = ZonedDateTime.ofInstant(
                scheduledRunTime.toInstant(), ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now();
        LOG.log(TRACE, () -> "Current trigger: " + nextField + ", scheduledRunTime: " + scheduledRunTimeJT);
        return scheduledRunTimeJT.plus(secondsLate, ChronoUnit.SECONDS).isBefore(now);
    }

    public void addTrigger(CronTrigger trigger, long skipIfLateBySeconds) {
        triggers.add(new LateAwareTrigger(trigger, skipIfLateBySeconds));
    }

    private record LateAwareTrigger(CronTrigger trigger, long skipIfLateBySeconds) {

    }
}
