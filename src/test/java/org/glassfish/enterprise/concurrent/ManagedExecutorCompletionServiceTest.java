package org.glassfish.enterprise.concurrent;

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.concurrent.ManagedTask;
import jakarta.enterprise.concurrent.ManagedTaskListener;
import org.glassfish.enterprise.concurrent.test.TestContextService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class ManagedExecutorCompletionServiceTest {

    static AbstractManagedExecutorService mes;

    ManagedExecutorCompletionService<Integer> mecs;

    @BeforeClass
    public static void beforeClass() throws Exception {
        BlockingQueue<Runnable> queue = new SynchronousQueue<>();
        mes = new ManagedExecutorServiceImpl("mes", null, 0, false,
                1, 10, 0, TimeUnit.SECONDS, 0L,
                new TestContextService(null), AbstractManagedExecutorService.RejectPolicy.ABORT,
                queue);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        mes.shutdownNow();
    }

    @Before
    public void setUp() throws Exception {
        mecs = new ManagedExecutorCompletionService<>(mes);
    }

    @Test(expected = NullPointerException.class)
    public void submit_NullCallableTask() {
        mecs.submit(null);
    }

    @Test(expected = NullPointerException.class)
    public void submit_NullRunnableTask() {
        mecs.submit(null, 1);
    }

    @Test
    public void submit_NotManagedTask_NullTaskListener() throws ExecutionException, InterruptedException {
        Runnable runnable = () -> System.out.println("Runnable");
        mecs.submit(runnable, 1);
        int i = mecs.take().get();
        Assert.assertEquals(1, i);
    }

    @Test
    public void submit_NotManagedTask_WithTaskListener() throws InterruptedException, ExecutionException {
        Runnable runnable = () -> System.out.println("Runnable");
        mecs.submit(runnable, 1, new ManagedTaskListener() {
            @Override
            public void taskSubmitted(Future<?> future, ManagedExecutorService executor, Object task) {
                Assert.assertTrue(task instanceof ManagedTask);
            }

            @Override
            public void taskAborted(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
                Assert.fail("Not expecting a failure here!");
            }

            @Override
            public void taskDone(Future<?> future, ManagedExecutorService executor, Object task, Throwable exception) {
                Assert.assertTrue(task instanceof ManagedTask);
            }

            @Override
            public void taskStarting(Future<?> future, ManagedExecutorService executor, Object task) {
                Assert.assertTrue(task instanceof ManagedTask);
            }
        });

        int i = mecs.take().get();
        Assert.assertEquals(1, i);
    }
}