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

package org.glassfish.enterprise.concurrent.internal;

import java.util.Map;
import jakarta.concurrency.ManagedTask;


public class Util {
    
    public static String getIdentityName(Object task) {
        // return the IDENTITY_NAME execution property value if task
        // implements ManagedTask and provides a non-null value for the
        // IDENTITY_NAME property
        if (task instanceof ManagedTask) {
            Map<String, String> executionProperties = ((ManagedTask)task).getExecutionProperties();
            if (executionProperties != null) {
                String taskIdentityName = executionProperties.get(ManagedTask.IDENTITY_NAME);
                if (taskIdentityName != null) {
                    return taskIdentityName;
                }
            }
        }
        // otherwise, return toString() as the identity name
        return task.toString();
    }
}
