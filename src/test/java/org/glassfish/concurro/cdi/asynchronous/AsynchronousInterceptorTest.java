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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

public class AsynchronousInterceptorTest {
    @Test
    public void testGettingCronTriggerFromScheduleWithCronExpression() {
        var scheduleWithCronExpression = ScheduleStub.newScheduleWithCronExpression("*/5 * * * * *");

        var trigger = AsynchronousInterceptor.getCronTrigger(scheduleWithCronExpression, null);

        var representation = trigger.toString();
        assert representation.matches("CronTrigger@.* seconds 0,5,10,15,20,25,30,35,40,45,50,55, \\* \\* \\* \\* \\*") : representation;
    }

    @Test
    public void testGettingCronTriggerFromSchedule() {
        var scheduleWithDefaults = ScheduleStub.newScheduleWithDefaults();

        var trigger = AsynchronousInterceptor.getCronTrigger(scheduleWithDefaults, null);

        var representation = trigger.toString();
        assert representation.matches("CronTrigger@.* seconds 0, minutes 0, hours 0, \\* \\* \\*") : representation;
    }

    @Test
    public void testGettingCronTriggerFromScheduleEmptySeconds() {
        var scheduleWithDefaults = ScheduleStub.newScheduleWithSeconds(new int[] {});

        var trigger = AsynchronousInterceptor.getCronTrigger(scheduleWithDefaults, null);

        var representation = trigger.toString();
        assert representation.matches("CronTrigger@.* \\* minutes 0, hours 0, \\* \\* \\*") : representation;
    }

    @Test
    public void testGettingCronTriggerFromScheduleEmptyMinutes() {
        var scheduleWithDefaults = ScheduleStub.newScheduleWithMinutes(new int[] {});

        var trigger = AsynchronousInterceptor.getCronTrigger(scheduleWithDefaults, null);

        var representation = trigger.toString();
        assert representation.matches("CronTrigger@.* seconds 0, \\* hours 0, \\* \\* \\*") : representation;
    }

    @Test
    public void testGettingCronTriggerFromScheduleEmptyHours() {
        var scheduleWithDefaults = ScheduleStub.newScheduleWithHours(new int[] {});

        var trigger = AsynchronousInterceptor.getCronTrigger(scheduleWithDefaults, null);

        var representation = trigger.toString();
        assert representation.matches("CronTrigger@.* seconds 0, minutes 0, \\* \\* \\* \\*") : representation;
    }

    @Test
    public void testGettingCronTriggerFromScheduleHMS() {
        var scheduleWithDefaults = ScheduleStub.newScheduleWithHMS(new int[] {11, 20},
                new int[] {19, 29, 39},
                new int[] {17, 23, 32});

        var trigger = AsynchronousInterceptor.getCronTrigger(scheduleWithDefaults, null);

        var representation = trigger.toString();
        assert representation.matches("CronTrigger@.* seconds 17,23,32, minutes 19,29,39, hours 11,20, \\* \\* \\*") : representation;
    }

    @Test
    void allHoursContents() {
        assert AsynchronousInterceptor.ALL_HOURS.length == 24;
        IntStream.rangeClosed(0, 23)
                .forEach(hour -> { assert Arrays.binarySearch(AsynchronousInterceptor.ALL_HOURS, hour) >=0: "Missing hour: " + hour; });
    }

    @Test
    void allMinutesContents() {
        assert AsynchronousInterceptor.ALL_MINUTES.length == 60;
        IntStream.rangeClosed(0, 59)
                .forEach(minute -> { assert Arrays.binarySearch(AsynchronousInterceptor.ALL_MINUTES, minute) >=0: "Missing minute: " + minute; });
    }

    @Test
    void allSecondsContents() {
        assert AsynchronousInterceptor.ALL_SECONDS.length == 60;
        IntStream.rangeClosed(0, 59)
                .forEach(second -> { assert Arrays.binarySearch(AsynchronousInterceptor.ALL_SECONDS, second) >=0: "Missing second: " + second; });
    }
}
