/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.concurro.test;

import java.lang.System.Logger;
import java.time.Instant;
import java.util.ArrayList;

import static java.lang.System.Logger.Level.INFO;

/**
 * A Runnable for use in scheduleAtFixedRate, scheduleWithFixedDelay, and
 * schedule with Trigger tests.
 */
public class TimeRecordingTestRunnable extends ManagedRunnableTestTask {
    private static final Logger LOG = System.getLogger(TimeRecordingTestRunnable.class.getName());

    private ArrayList<Long> invocations = new ArrayList<>();

    public TimeRecordingTestRunnable(ManagedTestTaskListener taskListener) {
        super(taskListener);
        invocations.add(System.currentTimeMillis());
    }

    public TimeRecordingTestRunnable(ManagedTestTaskListener taskListener, RuntimeException runException) {
        super(taskListener, runException);
        invocations.add(System.currentTimeMillis());
    }


    @Override
    public void run() {
        synchronized (invocations) {
            LOG.log(INFO, "run() executed at " + Instant.now());
            invocations.add(System.currentTimeMillis());
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.run();
    }

    public ArrayList<Long> getInvocations() {
        ArrayList<Long> result = new ArrayList<Long>();
        synchronized(invocations) {
            for(long time: invocations) {
                result.add(time);
            }
        }
        return result;
    }
}
