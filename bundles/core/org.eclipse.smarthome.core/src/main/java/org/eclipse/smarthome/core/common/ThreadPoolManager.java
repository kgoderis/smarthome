/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.core.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class provides a general mechanism to create thread pools. In general, no code of Eclipse SmartHome
 * should deal with its own pools, but rather use this class.
 * The created thread pools have named threads, so that it is easy to find them in the debugger. Additionally, it is
 * possible to configure the pool sizes through the configuration admin service, so that solutions have the chance to
 * tweak the pool sizes according to their needs.
 * </p>
 * <p>
 * The configuration can be done as
 * <br/>
 * {@code org.eclipse.smarthome.threadpool:<poolName>=<poolSize>[,<maxSize>]}
 * <br/>
 * where maxSize is only applicable for non-scheduled thread pools and is the number of maximum threads to create.
 * All threads will time out after {@link THREAD_TIMEOUT}.
 * </p>
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Karel Goderis - Addition of the ExpressionThreadPoolExecutor
 *
 */
public class ThreadPoolManager {

    private final static Logger logger = LoggerFactory.getLogger(ThreadPoolManager.class);

    private static final int DEFAULT_THREAD_POOL_MAX_SIZE = 10;
    private static final int DEFAULT_THREAD_POOL_CORE_SIZE = 5;

    private static final long THREAD_TIMEOUT = 65L;

    static private Map<String, ExecutorService> pools = new WeakHashMap<>();

    static private Map<String, int[]> configs = new ConcurrentHashMap<>();

    protected void activate(Map<String, Object> properties) {
        modified(properties);
    }

    protected void modified(Map<String, Object> properties) {
        for (Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().equals("service.pid") || entry.getKey().equals("component.id")
                    || entry.getKey().equals("component.name")) {
                continue;
            }
            String poolName = entry.getKey();
            Object config = entry.getValue();
            if (config == null) {
                configs.remove(poolName);
            }
            if (config instanceof String) {
                String[] segments = ((String) config).split(",");
                if (segments.length > 2) {
                    logger.warn("Ignoring invalid configuration for pool '{}': {} - config have at most 2 parts",
                            new Object[] { poolName, config });
                    continue;
                }
                try {
                    Integer coreSize = Integer.valueOf(segments[0]);
                    int[] cfg = (segments.length == 1)
                            ? new int[] { coreSize,
                                    coreSize > DEFAULT_THREAD_POOL_MAX_SIZE ? coreSize : DEFAULT_THREAD_POOL_MAX_SIZE }
                            : new int[] { coreSize, Integer.valueOf(segments[1]) };
                    if (cfg[0] > cfg[1]) {
                        logger.warn(
                                "Ignoring invalid configuration for pool '{}': {} - max value must be bigger than min value",
                                new Object[] { poolName, config });
                        continue;
                    }
                    if (cfg[0] < 0 || cfg[1] < 0) {
                        logger.warn("Ignoring invalid configuration for pool '{}': {} - value must not be negative",
                                new Object[] { poolName, config });
                        continue;
                    }
                    configs.put(poolName, cfg);
                    ThreadPoolExecutor pool = (ThreadPoolExecutor) pools.get(poolName);
                    if (pool != null) {
                        if (pool instanceof ScheduledExecutorService) {
                            // we only need to set the core pool size here
                            pool.setCorePoolSize(cfg[0]);
                            logger.debug("Updated scheduled thread pool '{}' to size {}",
                                    new Object[] { poolName, cfg[0] });
                        } else {
                            pool.setCorePoolSize(cfg[0]);
                            pool.setMaximumPoolSize(cfg[1]);
                            logger.debug("Updated thread pool '{}' to size {}-{}",
                                    new Object[] { poolName, cfg[0], cfg[1] });
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Ignoring invalid configuration for pool '{}': {} - entries must be integer values",
                            new Object[] { poolName, config });
                    continue;
                }
            }
        }
    }

    /**
     * Returns an instance of a scheduled thread pool service. If it is the first request for the given pool name, the
     * instance is newly created.
     *
     * @param poolName a short name used to identify the pool, e.g. "discovery"
     * @return an instance to use
     */
    static public ScheduledExecutorService getScheduledPool(String poolName) {
        ExecutorService pool = pools.get(poolName);
        if (pool == null) {
            synchronized (pools) {
                // do a double check if it is still null or if another thread might have created it meanwhile
                pool = pools.get(poolName);
                if (pool == null) {
                    int[] cfg = getConfig(poolName);
                    pool = Executors.newScheduledThreadPool(cfg[0], new NamedThreadFactory(poolName));
                    ((ThreadPoolExecutor) pool).setKeepAliveTime(THREAD_TIMEOUT, TimeUnit.SECONDS);
                    ((ThreadPoolExecutor) pool).allowCoreThreadTimeOut(true);
                    pools.put(poolName, pool);
                    logger.debug("Created scheduled thread pool '{}' of size {}", new Object[] { poolName, cfg[0] });
                }
            }
        }
        if (pool instanceof ScheduledExecutorService) {
            return (ScheduledExecutorService) pool;
        } else {
            throw new IllegalArgumentException("Pool " + poolName + " is not a scheduled pool!");
        }
    }

    /**
     * Returns an instance of a cached thread pool service. If it is the first request for the given pool name, the
     * instance is newly created.
     *
     * @param poolName a short name used to identify the pool, e.g. "discovery"
     * @return an instance to use
     */
    static public ExecutorService getPool(String poolName) {
        ExecutorService pool = pools.get(poolName);
        if (pool == null) {
            synchronized (pools) {
                // do a double check if it is still null or if another thread might have created it meanwhile
                pool = pools.get(poolName);
                if (pool == null) {
                    int[] cfg = getConfig(poolName);
                    pool = new CommonThreadExecutor(poolName, cfg[0], cfg[1]);
                    ((ThreadPoolExecutor) pool).setKeepAliveTime(THREAD_TIMEOUT, TimeUnit.SECONDS);
                    ((ThreadPoolExecutor) pool).allowCoreThreadTimeOut(true);
                    pools.put(poolName, pool);
                    logger.debug("Created thread pool '{}' with size {}-{}", new Object[] { poolName, cfg[0], cfg[1] });
                }
            }
        }
        return pool;
    }

    /**
     * Returns an instance of an expression-driven scheduled thread pool service. If it is the first request for the
     * given pool name, the
     * instance is newly created.
     *
     * @param poolName a short name used to identify the pool, e.g. "discovery"
     * @return an instance to use
     */
    static public ExpressionThreadPoolExecutor getExpressionScheduledPool(String poolName) {
        ExecutorService pool = pools.get(poolName);
        if (pool == null) {
            synchronized (pools) {
                // do a double check if it is still null or if another thread might have created it meanwhile
                pool = pools.get(poolName);
                if (pool == null) {
                    int[] cfg = getConfig(poolName);
                    pool = new ExpressionThreadPoolExecutor(poolName, cfg[0]);
                    ((ThreadPoolExecutor) pool).setKeepAliveTime(THREAD_TIMEOUT, TimeUnit.SECONDS);
                    ((ThreadPoolExecutor) pool).allowCoreThreadTimeOut(true);
                    pools.put(poolName, pool);
                    logger.debug("Created an expression-drive scheduled thread pool '{}' of size {}",
                            new Object[] { poolName, cfg[0] });
                }
            }
        }
        if (pool instanceof ExpressionThreadPoolExecutor) {
            return (ExpressionThreadPoolExecutor) pool;
        } else {
            throw new IllegalArgumentException("Pool " + poolName + " is not an expression-driven scheduled pool!");
        }
    }

    private static int[] getConfig(String poolName) {
        int[] cfg = configs.get(poolName);
        return (cfg != null) ? cfg : new int[] { DEFAULT_THREAD_POOL_CORE_SIZE, DEFAULT_THREAD_POOL_MAX_SIZE };
    }

    private static class CommonThreadExecutor extends ThreadPoolExecutor {

        public CommonThreadExecutor(final String poolName, int corePoolSize, int maxPoolSize) {
            this(poolName, corePoolSize, maxPoolSize, new NamedThreadFactory(poolName),
                    new ThreadPoolExecutor.DiscardPolicy() {
                        // The pool is bounded and rejections will happen during shutdown
                        @Override
                        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor) {
                            // Log and discard
                            logger.warn("Thread pool '{}' rejected execution of {}",
                                    new Object[] { poolName, runnable.getClass() });
                            super.rejectedExecution(runnable, threadPoolExecutor);
                        }
                    });
        }

        public CommonThreadExecutor(String threadPool, int corePoolSize, int maxPoolSize, ThreadFactory threadFactory,
                RejectedExecutionHandler rejectedHandler) {
            // This is the same as Executors.newCachedThreadPool
            super(corePoolSize, maxPoolSize, THREAD_TIMEOUT, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                    threadFactory, rejectedHandler);
        }

        @Override
        protected void afterExecute(Runnable runnable, Throwable throwable) {
            super.afterExecute(runnable, throwable);
            if (throwable != null) {
                Throwable cause = throwable.getCause();
                if (cause instanceof InterruptedException) {
                    // Ignore this, might happen when we shutdownNow() the executor. We can't
                    // log at this point as the logging system might be stopped already.
                    return;
                }
            }
        }
    }

    public static class ExpressionThreadPoolExecutor extends ScheduledThreadPoolExecutor {

        private List<Runnable> running = Collections.synchronizedList(new ArrayList<Runnable>());
        private Map<Runnable, Runnable> scheduled = Collections.synchronizedMap(new HashMap<Runnable, Runnable>());
        private Map<Runnable, Future<?>> futures = Collections.synchronizedMap(new HashMap<Runnable, Future<?>>());

        public ExpressionThreadPoolExecutor(final String poolName, int corePoolSize) {
            this(poolName, corePoolSize, new NamedThreadFactory(poolName), new ThreadPoolExecutor.DiscardPolicy() {
                // The pool is bounded and rejections will happen during shutdown
                @Override
                public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor) {
                    // Log and discard
                    logger.warn("Thread pool '{}' rejected execution of {}",
                            new Object[] { poolName, runnable.getClass() });
                    super.rejectedExecution(runnable, threadPoolExecutor);
                }
            });
        }

        public ExpressionThreadPoolExecutor(String threadPool, int corePoolSize, ThreadFactory threadFactory,
                RejectedExecutionHandler rejectedHandler) {
            super(corePoolSize, threadFactory, rejectedHandler);
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable runnable) {
            super.beforeExecute(thread, runnable);
            running.add(runnable);
        }

        @Override
        protected void afterExecute(Runnable runnable, Throwable throwable) {
            super.afterExecute(runnable, throwable);
            running.remove(runnable);
            futures.remove(runnable);
            scheduled.remove(runnable);
            if (throwable != null) {
                Throwable cause = throwable.getCause();
                if (cause instanceof InterruptedException) {
                    // Ignore this, might happen when we shutdownNow() the executor. We can't
                    // log at this point as the logging system might be stopped already.
                    return;
                }
            }
        }

        public void schedule(final Runnable task, final Expression expression) {
            if (task == null) {
                throw new NullPointerException();
            }

            this.setCorePoolSize(this.getCorePoolSize() + 1);

            Runnable scheduleTask = new Runnable() {

                @Override
                public void run() {
                    Date now = new Date();
                    Date time = expression.getTimeAfter(now);

                    try {
                        while (time != null) {
                            futures.put(task, ExpressionThreadPoolExecutor.this.schedule(task,
                                    time.getTime() - now.getTime(), TimeUnit.MILLISECONDS));
                            scheduled.put(task, this);

                            while (now.before(time)) {
                                Thread.sleep(time.getTime() - now.getTime());
                                now = new Date();
                            }

                            time = expression.getTimeAfter(now);
                        }
                    } catch (RejectedExecutionException e) {
                        logger.error("The executor has already shutdown : '{}'", e.getMessage());
                    } catch (CancellationException e) {
                        logger.error("Non executed tasks are cancelled : '{}'", e.getMessage());
                    } catch (InterruptedException e) {
                        logger.error("Executing tasks are cancelled : '{}'", e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }
            };

            futures.put(scheduleTask, this.submit(scheduleTask));
        }

        @Override
        public boolean remove(Runnable task) {
            Runnable scheduledTask = scheduled.get(task);
            if (futures.get(task) != null && futures.get(task).cancel(false)) {
                running.remove(task);
            }
            if (futures.get(scheduledTask) != null && futures.get(scheduledTask).cancel(true)) {
                running.remove(scheduledTask);
            }
            scheduled.remove(task);
            return super.remove(task);
        }

        public List<Runnable> getRunning() {
            return running;
        }
    }

    /**
     * This is a normal thread factory, which adds a named prefix to all created threads.
     */
    private static class NamedThreadFactory implements ThreadFactory {

        protected final ThreadGroup group;
        protected final AtomicInteger threadNumber = new AtomicInteger(1);
        protected final String namePrefix;

        public NamedThreadFactory(String threadPool) {
            this.namePrefix = "ESH-" + threadPool + "-";
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }

            return t;
        }
    }

}
