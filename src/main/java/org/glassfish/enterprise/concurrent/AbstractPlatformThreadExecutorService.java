package org.glassfish.enterprise.concurrent;

import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Abstract base class for {@code ManagedExecutorService}s, which are based on
 * platform threads.
 *
 * @author Petr Aubrecht
 */
public abstract class AbstractPlatformThreadExecutorService extends AbstractManagedExecutorService {

    public AbstractPlatformThreadExecutorService(String name, ManagedThreadFactoryImpl managedThreadFactory, long hungTaskThreshold, boolean longRunningTasks, ContextServiceImpl contextService, ContextSetupProvider contextCallback, RejectPolicy rejectPolicy) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks, contextService, contextCallback, rejectPolicy);
    }

    @Override
    protected boolean isTaskHung(Thread thread, long now) {
        AbstractManagedThread managedThread = (AbstractManagedThread) thread;
        return managedThread.isTaskHung(now);
    }

}
