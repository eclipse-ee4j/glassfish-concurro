package org.glassfish.enterprise.concurrent;

import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Abstract base class for {@code ManagedExecutorService}s, which are based on platform threads.
 *
 * @author Petr Aubrecht
 */
public abstract class AbstractPlatformThreadExecutorService extends AbstractManagedExecutorService {

    protected final ManagedThreadFactoryImpl managedThreadFactory; // FIXME aubi replace with virtual method getManagedThreadFactory

    public AbstractPlatformThreadExecutorService(String name, ManagedThreadFactoryImpl managedThreadFactory, long hungTaskThreshold, boolean longRunningTasks, ContextServiceImpl contextService, ContextSetupProvider contextCallback, RejectPolicy rejectPolicy) {
        super(name, longRunningTasks, contextService, contextCallback, rejectPolicy);

        this.managedThreadFactory = managedThreadFactory != null ? managedThreadFactory : createDefaultManagedThreadFactory(name);
        this.managedThreadFactory.setHungTaskThreshold(hungTaskThreshold);

    }

    @Override
    protected boolean isTaskHung(Thread thread, long now) {
        AbstractManagedThread managedThread = (AbstractManagedThread) thread;
        return managedThread.isTaskHung(now);
    }

    @Override
    public ManagedThreadFactoryImpl getManagedThreadFactory() {
        return managedThreadFactory;
    }

    private ManagedThreadFactoryImpl createDefaultManagedThreadFactory(String name) {
        return new ManagedThreadFactoryImpl(name + "-ManagedThreadFactory",
                null,
                Thread.NORM_PRIORITY);
    }

}
