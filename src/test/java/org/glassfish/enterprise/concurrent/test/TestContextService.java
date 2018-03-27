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

package org.glassfish.enterprise.concurrent.test;

import java.util.Map;
import org.glassfish.enterprise.concurrent.ContextServiceImpl;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;
import org.glassfish.enterprise.concurrent.spi.TransactionSetupProvider;

public class TestContextService extends ContextServiceImpl {

    public TestContextService(ContextSetupProvider contextSetupProvider) {
        super("test context", contextSetupProvider, null);
    }

    public TestContextService(String name, ContextSetupProvider contextSetupProvider,
            TransactionSetupProvider transactionSetupProvider) {
        super(name, contextSetupProvider, transactionSetupProvider);
    }

    @Override
    public Object createContextualProxy(Object instance, Class[] interfaces) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object createContextualProxy(Object instance, Map<String, String> contextProperties, Class<?>... interfaces) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Map<String, String> getExecutionProperties(Object contextObject) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
