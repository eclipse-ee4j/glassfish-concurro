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

package org.glassfish.enterprise.concurrent.spi;

/**
 * Constants for standard types of Context
 */
public class ContextInfo {
   
    // if enabled, propagate the container security principle
    public static final String SECURITY = "security";
    
    // if enabled, the locale from the container thread is propagated
    public static final String LOCALE = "locale";
    
    // if enabled, custom, thread-local data is propagated
    public static final String CUSTOM = "custom";
}
