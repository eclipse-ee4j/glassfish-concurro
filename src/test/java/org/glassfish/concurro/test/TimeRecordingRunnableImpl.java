/*
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

import java.util.ArrayList;

/**
 * A Runnable for use in scheduleAtFixedRate, scheduleWithFixedDelay, and 
 * schedule with Trigger tests.
 */
public class TimeRecordingRunnableImpl extends ManagedRunnableTask {

    ArrayList<Long> invocations = new ArrayList<>();

    public boolean DEBUG;
    
    public TimeRecordingRunnableImpl(ManagedTaskListenerImpl taskListener) {
        super(taskListener);
        invocations.add(System.currentTimeMillis());
    }

    public TimeRecordingRunnableImpl(ManagedTaskListenerImpl taskListener, RuntimeException runException) {
        super(taskListener, runException);
        invocations.add(System.currentTimeMillis());
    }
    
    
    @Override
    public void run() {
        try {
            synchronized(invocations) {
                if (DEBUG) System.out.println("TimeRecordingRunnableImpl.run()" + new java.util.Date(System.currentTimeMillis()));
                invocations.add(System.currentTimeMillis());
            }
            // sleep for 1 second
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            
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
