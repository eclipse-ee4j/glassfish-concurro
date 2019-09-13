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
import jakarta.enterprise.concurrent.ContextService;
import org.glassfish.enterprise.concurrent.spi.ContextHandle;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

public class ClassloaderContextSetupProvider implements ContextSetupProvider {

    private String classloaderName;
    public transient ClassLoader classloaderBeforeSetup; // for test verification
    public volatile int numResetCalled = 0;
    public volatile Map<String, String> contextServiceProperties; // for test verification
    public String throwsOnSetupMessage = null;

    public ClassloaderContextSetupProvider(String classloaderName) {
        this.classloaderName = classloaderName;
    }
    
    public void throwsOnSetup(String msg) {
        throwsOnSetupMessage = msg;
    }
    
    @Override
    public ContextHandle saveContext(ContextService contextService) {
//        System.out.println("ClassloaderContextSetupProvider.saveContext called");
//        new Exception("ClassloaderContextSetupProvider.saveContext").printStackTrace();
        return new SavedContext(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public ContextHandle setup(ContextHandle contextHandle) {
        if (throwsOnSetupMessage != null) {
            throw new IllegalStateException(throwsOnSetupMessage);
        }
        classloaderBeforeSetup = Thread.currentThread().getContextClassLoader();
        
        SavedContext savedContext = (SavedContext)contextHandle;
        ClassLoader contextClassLoader = 
                new NamedClassLoader(classloaderName, savedContext.originalClassloader);
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        return new SavedContext(classloaderBeforeSetup);
    }

    @Override
    public void reset(ContextHandle contextHandle) {
        numResetCalled++;
        SavedContext savedContext = (SavedContext)contextHandle;
        Thread.currentThread().setContextClassLoader(savedContext.originalClassloader);
    }

    @Override
    public ContextHandle saveContext(ContextService contextService, Map<String, String> contextObjectProperties) {
        contextServiceProperties = contextObjectProperties;
        return new SavedContext(Thread.currentThread().getContextClassLoader());
    }
    
    static class SavedContext implements ContextHandle {
        transient ClassLoader originalClassloader;

        public SavedContext(ClassLoader originalClassloader) {
            this.originalClassloader = originalClassloader;
        }
                
    }
    
}
