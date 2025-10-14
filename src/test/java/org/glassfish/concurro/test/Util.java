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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Util {

    private static final long MAX_WAIT_TIME = 10000L; // 10 seconds

    public static interface BooleanValueProducer {
      public boolean getValue();
    }

    public static boolean waitForBoolean(BooleanValueProducer valueProducer, boolean expectedValue, String loggerName) {
      long endWaitTime = System.currentTimeMillis() + MAX_WAIT_TIME;
      boolean value = valueProducer.getValue();
      while ( (value != expectedValue) &&
              endWaitTime > System.currentTimeMillis()) {
          try {
              Thread.sleep(100);
          } catch (InterruptedException ex) {
              Logger.getLogger(loggerName).log(Level.SEVERE, null, ex);
          }
        value = valueProducer.getValue();
      }
      return value;
    }

    public static boolean waitForTaskStarted(final Future<?> future, final ManagedTestTaskListener listener, String loggerName) {
      return waitForBoolean(
          new BooleanValueProducer() {
            public boolean getValue() {
              return listener.eventCalled(future, listener.STARTING);
            }
          },
          true, loggerName);
    }

    public static boolean waitForTaskComplete(final FakeRunnableForTest task, String loggerName) {
      return waitForBoolean(
          new BooleanValueProducer() {
            public boolean getValue() {
              return task.runCalled;
            }
          },
          true,loggerName);
    }

    public static boolean waitForTaskAborted(final Future<?> future, final ManagedTestTaskListener listener, String loggerName) {
      return waitForBoolean(
          new BooleanValueProducer() {
            public boolean getValue() {
              return listener.eventCalled(future, listener.ABORTED);
            }
          },
          true, loggerName);
    }

  public static boolean waitForTaskDone(final Future<?> future, final ManagedTestTaskListener listener, String loggerName) {
    return waitForBoolean(
        new BooleanValueProducer() {
          public boolean getValue() {
            return listener.eventCalled(future, listener.DONE);
          }
        },
        true, loggerName);
  }


    public static String generateName() {
        return new java.util.Date(System.currentTimeMillis()).toString();
    }

    public static void log(String message) {
        System.out.println(DateTimeFormatter.ISO_TIME.format(LocalDateTime.now()) + ": " + message);
    }

}
