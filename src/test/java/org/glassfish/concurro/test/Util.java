/*
 * Copyright (c) 2023, 2025 Contributors to the Eclipse Foundation.
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

public class Util {

    private static final long MAX_WAIT_TIME = 10000L; // 10 seconds

    /**
     * Ignores {@link Exception}s and {@link AssertionError}s for {@value #MAX_WAIT_TIME} millis
     * at most.
     *
     * @param action action to repeat
     * @throws Exception
     * @throws AssertionError
     */
    public static void retry(Action action) throws AssertionError, Exception {
        long endWaitTime = System.currentTimeMillis() + MAX_WAIT_TIME;
        while (endWaitTime > System.currentTimeMillis()) {
            try {
                action.action();
                return;
            } catch (AssertionError | Exception e) {
                Thread.onSpinWait();
                continue;
            }
        }
        action.action();
    }

    @FunctionalInterface
    public interface Action {
        void action() throws AssertionError, Exception;
    }
}
