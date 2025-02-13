/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package org.glassfish.concurro.cdi.asynchronous;

import jakarta.enterprise.concurrent.Schedule;

import java.lang.annotation.Annotation;
import java.time.DayOfWeek;
import java.time.Month;

class ScheduleStub implements Schedule {
    private String cron = "";
    private Month[] months = {};
    private int[] daysOfMonth = {};
    private DayOfWeek[] daysOfWeek = {};
    private int[] hours = { 0 };
    private int[] minutes = { 0 };
    private int[] seconds = { 0 };
    private long skipIfLateBy = 600;
    private String zone = "";

    private ScheduleStub() {
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Schedule.class;
    }

    @Override
    public String cron() {
        return cron;
    }

    void cron(String cron) {
        this.cron = cron;
    }

    @Override
    public Month[] months() {
        return months;
    }

    @Override
    public int[] daysOfMonth() {
        return daysOfMonth;
    }

    @Override
    public DayOfWeek[] daysOfWeek() {
        return daysOfWeek;
    }

    @Override
    public int[] hours() {
        return hours;
    }

    @Override
    public int[] minutes() {
        return minutes;
    }

    @Override
    public int[] seconds() {
        return seconds;
    }

    @Override
    public long skipIfLateBy() {
        return skipIfLateBy;
    }

    @Override
    public String zone() {
        return zone;
    }

    static Schedule newScheduleWithCronExpression(String cron) {
        var schedule = new ScheduleStub();
        schedule.cron(cron);
        return schedule;
    }
}
