/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation.
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

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

public class BlockingRunnableImpl extends RunnableImpl {

    private long blockTime;
    private volatile boolean interrupted;
    private volatile boolean stopBlocking;

    private final System.Logger logger = System.getLogger(this.getClass().getName());

    public BlockingRunnableImpl(ManagedTaskListenerImpl taskListener,
            long blockTime) {
        super(taskListener);
        this.blockTime = blockTime;
    }

    private void busyWait() {
        // busy wait until stopBlocking is set
        debug("busyWait stopBlocking is " + stopBlocking);
        while (!stopBlocking) {
            try {
                logger.log(TRACE, "busyWait, sleeping, task = " + this);
                Thread.sleep(100 /*ms*/);
            } catch (InterruptedException e) {
                debug("busyWait, InterruptedException, task = " + this);
                interrupted = true;
            }
        }
        debug("done busyWait. interrupted=" + interrupted );
    }

    private void blockForSpecifiedTime() {
        // blocks until timed out or interrupted
        try {
            Thread.sleep(blockTime);
        }
        catch (InterruptedException e) {
            interrupted = true;
        }
    }

    public void run() {
        debug("BlockingRunnableImpl.run() " + this);
        if (blockTime == 0) {
            busyWait();
        } else {
            blockForSpecifiedTime();
        }
        debug("BlockingRunnableImpl.run() done " + this);
        runCalled = true;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void stopBlocking() {
        stopBlocking = true;
    }

    public void debug(String msg) {
        logger.log(DEBUG, msg);
    }
}
