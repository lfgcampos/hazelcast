package com.hazelcast.concurrent.lock;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.SlowTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category(SlowTest.class)
public class LockStressTest extends HazelcastTestSupport {

    /**
     * Test for issue 267
     */
    @Test(timeout = 1000 * 100)
    public void testHighConcurrentLockAndUnlock() {
        final TestHazelcastInstanceFactory nodeFactory = createHazelcastInstanceFactory(1);
        final HazelcastInstance hz = nodeFactory.newHazelcastInstance();
        final String key = "key";
        final int threadCount = 100;
        final int lockCountPerThread = 5000;
        final int locks = 50;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger totalCount = new AtomicInteger();

        class InnerTest implements Runnable {
            public void run() {
                boolean live = true;
                Random rand = new Random();
                try {
                    for (int j = 0; j < lockCountPerThread && live; j++) {
                        final Lock lock = hz.getLock(key + rand.nextInt(locks));
                        lock.lock();
                        try {
                            if (j % 100 == 0) {
                                System.out.println(Thread.currentThread().getName() + " is at:" + j);
                            }
                            totalCount.incrementAndGet();
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        } finally {
                            try {
                                lock.unlock();
                            } catch (Exception e) {
                                e.printStackTrace();
                                live = false;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }
        }

        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(new InnerTest());
        }

        try {
            assertTrue("Lock tasks stuck!", latch.await(2, TimeUnit.MINUTES));
            assertEquals((threadCount * lockCountPerThread), totalCount.get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                hz.getLifecycleService().terminate();
            } catch (Throwable ignored) {
            }
            executorService.shutdownNow();
        }
    }
}
