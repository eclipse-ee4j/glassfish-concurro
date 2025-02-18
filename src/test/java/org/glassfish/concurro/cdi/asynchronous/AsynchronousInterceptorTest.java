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

import org.junit.Test;

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
}
