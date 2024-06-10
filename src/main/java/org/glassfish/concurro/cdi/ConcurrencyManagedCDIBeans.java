/*
 * Copyright (c) 2024 Payara Foundation and/or its affiliates.
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
package org.glassfish.concurro.cdi;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Set of annotations of concurrency XYDefinitions, identified during annotation
 * scanning. Consist of the concurrency type (e.g. ManagedExecutorService),
 * user-defined qualifier class, and JNDI name.
 *
 * @author Petr Aubrecht
 */
public class ConcurrencyManagedCDIBeans {
    public enum Type {
        CONTEXT_SERVICE,
        MANAGED_THREAD_FACTORY,
        MANAGED_EXECUTOR_SERVICE,
        MANAGED_SCHEDULED_EXECUTOR_SERVICE
    };

    /**
     * Annotation scanner stores this instance in JNDI under JNDI_NAME name.
     */
    public static final String JNDI_NAME = "java:app/concurrent/__ConcurrencyManagedCDIBeans";

    private final Map<String, ConfiguredCDIBean> beans = new HashMap<>();

    public ConcurrencyManagedCDIBeans() {
    }

    public Map<String, ConfiguredCDIBean> getBeans() {
        return beans;
    }

    public void addDefinition(
            ConcurrencyManagedCDIBeans.Type concurrencyType,
            Set<String> qualifiers,
            String jndiName) {
        beans.put(jndiName, new ConfiguredCDIBean(concurrencyType, qualifiers));
    }

    public record ConfiguredCDIBean(
            ConcurrencyManagedCDIBeans.Type definitionType,
            Set<String> qualifiers) {

    }

    @Override
    public String toString() {
        return "ConcurrencyManagedCDIBeans{" + "beans=" + beans + '}';
    }
}
