package org.jcyclone.core.internal;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.*;

/**
 * A ThreadPoolTask executes a task using the pooled threads.
 */
public class ThreadPoolTask {

       /**
        * Permission for checking shutdown
        */
       private static final RuntimePermission shutdownPerm =
                       new RuntimePermission("modifyThread");

       private final Runnable task;

       /**
        * Lock held on updates to currentPoolSize, poolSize and
        * workers set.
        */
       private final ReentrantLock mainLock = new ReentrantLock();

       /**
        * Wait condition to support awaitTermination
        */
       private final Condition termination = mainLock.newCondition();

       /**
        * Set containing all worker threads in pool.
        */
       private final HashSet<Worker> workers = new HashSet<Worker>();

       /**
        * Pool size, updated only while holding mainLock,
        * but volatile to allow concurrent readability even
        * during updates.
        */
       private volatile int poolSize;

       /**
        * Current pool size, updated only while holding mainLock
        * but volatile to allow concurrent readability even
        * during updates.
        */
       private volatile int currentPoolSize;

       /**
        * Lifecycle state
        */
       volatile int runState = NOT_STARTED;

       // Special values for runState
       /** Initial state */
       static final int NOT_STARTED = -1;
       /** Normal, not-shutdown mode */
       static final int RUNNING    = 0;
       /** Controlled shutdown mode */
       static final int SHUTDOWN   = 1;
       /** Immediate shutdown mode */
       static final int STOP       = 2;
       /** Final state */
       static final int TERMINATED = 3;

       /**
        * Factory for new threads.
        */
       private volatile ThreadFactory threadFactory;

       /**
        * Tracks largest attained pool size.
        */
       private int largestPoolSize;


       /**
        * Create and return a new thread running task. Call only while
holding mainLock
        * @return the new thread, or null if threadFactory fails to create thread
        */
       private Thread addThread() {
               Worker w = new Worker();
               Thread t = threadFactory.newThread(w);
               if (t != null) {
                       w.thread = t;
                       workers.add(w);
                       int nt = ++currentPoolSize;
                       if (nt > largestPoolSize)
                               largestPoolSize = nt;
               }
               return t;
       }

       /**
        * Create and start a new thread running task, only if
        * fewer than poolSize threads are running.
        * @return true if successful.
        */
       private boolean addIfUnderPoolSize() {
               Thread t = null;
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       if (currentPoolSize < poolSize)
                               t = addThread();
               } finally {
                       mainLock.unlock();
               }
               if (t == null)
                       return false;
               t.start();
               return true;
       }

       /**
        * Perform bookkeeping for a terminated worker thread.
        * @param w the worker
        */
       void workerDone(Worker w) {
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       workers.remove(w);
                       if (--currentPoolSize > 0)
                               return;

                       // Else, this is the last thread. Deal with potential shutdown.

                       int state = runState;
                       assert state == STOP || state == SHUTDOWN;

                       // Either state is STOP, or state is SHUTDOWN and there is
                       // no work to do. So we can terminate.
                       termination.signalAll();
                       runState = TERMINATED;
                       // fall through to call terminate() outside of lock.
               } finally {
                       mainLock.unlock();
               }

               assert runState == TERMINATED;
               terminated();
       }

       /**
        *  Worker threads
        */
       private class Worker implements Runnable {

               private volatile boolean stop = false;

               /**
                * Thread this worker is running in.  Acts as a final field,
                * but cannot be set until thread is created.
                */
               Thread thread;

               /**
                * Signals the Worker it should stop working.
                */
               void stop() {
                       stop = true;
               }

               /**
                * Cause thread to die even if running a task.
                */
               void stopNow() {
                       stop = true;
                       thread.interrupt();
               }

               /**
                * Main run loop
                */
               public void run() {
                       try {
                               while (runState == RUNNING && !stop) {
                                       task.run();
                               }
                       } finally {
                               workerDone(this);
                       }
               }
       }

       // Public methods

       /**
        * Creates a new <tt>ThreadPoolTask</tt> with the given
        * initial parameters and default thread factory.
        *
        * @param poolSize the number of threads to keep in the pool.
        * @param task the task to execute by the pool.
        * @throws IllegalArgumentException if poolSize less than zero.
        * @throws NullPointerException if <tt>task</tt> is null
        */
       public ThreadPoolTask(int poolSize,
                             Runnable task) {
               this(poolSize, task, Executors.defaultThreadFactory());
       }

       /**
        * Creates a new <tt>ThreadPoolTask</tt> with the given initial
        * parameters.
        *
        * @param corePoolSize the number of threads to keep in the pool.
        * @param task the task to execute by the pool.
        * @param threadFactory the factory to use when a new thread is created.
        * @throws IllegalArgumentException if corePoolSize less than zero.
        * @throws NullPointerException if <tt>task</tt> or
<tt>threadFactory</tt> are null.
        */
       public ThreadPoolTask(int corePoolSize,
                             Runnable task,
                             ThreadFactory threadFactory) {

               if (corePoolSize < 0)
                       throw new IllegalArgumentException();
               if (task == null || threadFactory == null)
                       throw new NullPointerException();
               this.poolSize = corePoolSize;
               this.task = task;
               this.threadFactory = threadFactory;
       }

       /**
        * Starts all threads.
        * Invocation has no additional effect if already started.
        */
       public void start() {
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       if (runState != NOT_STARTED)
                               return;
                       runState = RUNNING;
               } finally {
                       mainLock.unlock();
               }

               while (addIfUnderPoolSize());
       }

       /**
        * Initiates an orderly shutdown in which currently running
        * tasks are executed, but no others tasks will be accepted.
        * Invocation has no additional effect if already shut
        * down.
        */
       public void shutdown() {

               boolean fullyTerminated = false;
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       if (workers.size() > 0) {

                               int state = runState;
                               if (state == RUNNING) // don't override shutdownNow
                                       runState = SHUTDOWN;

                       }
                       else { // If no workers, trigger full termination now
                               fullyTerminated = true;
                               runState = TERMINATED;
                               termination.signalAll();
                       }
               } finally {
                       mainLock.unlock();
               }
               if (fullyTerminated)
                       terminated();
       }


       /**
        * Attempts to stop all actively executing tasks.
        *
        * <p>This implementation cancels tasks via {@link
        * Thread#interrupt}, so if a task masks or fails to respond to
        * interrupts, it may never terminate.
        *
        * @throws SecurityException if a security manager exists and
        * shutting down this thread pool may manipulate threads that
        * the caller is not permitted to modify because it does not hold
        * {@link java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
        * or the security manager's <tt>checkAccess</tt> method denies access.
        */
       public void shutdownNow() {
               // Almost the same code as shutdown()
               SecurityManager security = System.getSecurityManager();
               if (security != null)
                       java.security.AccessController.checkPermission(shutdownPerm);

               boolean fullyTerminated = false;
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       if (workers.size() > 0) {
                               if (security != null) {
                                       for (Worker w: workers)
                                               security.checkAccess(w.thread);
                               }

                               int state = runState;
                               if (state != TERMINATED)
                                       runState = STOP;
                               try {
                                       for (Worker w : workers)
                                               w.stopNow();
                               } catch(SecurityException se) {
                                       runState = state; // back out;
                                       throw se;
                               }
                       }
                       else { // If no workers, trigger full termination now
                               fullyTerminated = true;
                               runState = TERMINATED;
                               termination.signalAll();
                       }
               } finally {
                       mainLock.unlock();
               }
               if (fullyTerminated)
                       terminated();
       }


       /**
        * Returns <tt>true</tt> if this thread pool has been shut down.
        *
        * @return <tt>true</tt> if this thread pool has been shut down.
        */
       public boolean isShutdown() {
               return runState != RUNNING;
       }

       /**
        * Returns true if this thread pool is in the process of terminating
        * after <tt>shutdown</tt> or <tt>shutdownNow</tt> but has not
        * completely terminated.  This method may be useful for
        * debugging. A return of <tt>true</tt> reported a sufficient
        * period after shutdown may indicate that submitted task have
        * ignored or suppressed interruption, causing this thread pool not
        * to properly terminate.
        * @return true if terminating but not yet terminated.
        */
       public boolean isTerminating() {
               return runState == STOP;
       }

       /**
        * Returns <tt>true</tt> if all threads have completed following shut down.
        * Note that <tt>isTerminated</tt> is never <tt>true</tt> unless
        * either <tt>shutdown</tt> or <tt>shutdownNow</tt> was called first.
        *
        * @return <tt>true</tt> if all threads have completed following shut down.
        */
       public boolean isTerminated() {
               return runState == TERMINATED;
       }

       /**
        * Blocks until all threads have completed execution after a shutdown
        * request, or the timeout occurs, or the current thread is
        * interrupted, whichever happens first.
        *
        * @param timeout the maximum time to wait
        * @param unit the time unit of the timeout argument
        * @return <tt>true</tt> if this thread pool terminated and <tt>false</tt>
        * if the timeout elapsed before termination
        * @throws InterruptedException if interrupted while waiting
        */
       public boolean awaitTermination(long timeout, TimeUnit unit)
                       throws InterruptedException {
               long nanos = unit.toNanos(timeout);
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       for (;;) {
                               if (runState == TERMINATED)
                                       return true;
                               if (nanos <= 0)
                                       return false;
                               nanos = termination.awaitNanos(nanos);
                       }
               } finally {
                       mainLock.unlock();
               }
       }

       /**
        * Invokes <tt>shutdown</tt> when this thread pool is no longer
        * referenced.
        */
       protected void finalize()  {
               shutdown();
       }

       /**
        * Sets the thread factory used to create new threads.
        *
        * @param threadFactory the new thread factory
        * @throws NullPointerException if threadFactory is null
        * @see #getThreadFactory
        */
       public void setThreadFactory(ThreadFactory threadFactory) {
               if (threadFactory == null)
                       throw new NullPointerException();
               this.threadFactory = threadFactory;
       }

       /**
        * Returns the thread factory used to create new threads.
        *
        * @return the current thread factory
        * @see #setThreadFactory
        */
       public ThreadFactory getThreadFactory() {
               return threadFactory;
       }

       /**
        * Sets the number of threads.  This overrides any value set
        * in the constructor.  If the new value is smaller than the
        * current value, excess existing threads will be terminated
        * after the task has been executed. If larger, new threads will
        * be started to execute the task.
        *
        * @param poolSize the new size
        * @throws IllegalArgumentException if <tt>poolSize</tt>
        * less than zero
        * @see #getPoolSize
        */
       public void setPoolSize(int poolSize) {
               if (poolSize < 0)
                       throw new IllegalArgumentException();
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       int extra = this.poolSize - poolSize;
                       this.poolSize = poolSize;
                       if (extra < 0) {
                               while (extra++ < 0 && currentPoolSize < poolSize) {
                                       Thread t = addThread();
                                       if (t != null)
                                               t.start();
                                       else
                                               break;
                               }
                       }
                       else if (extra > 0 && currentPoolSize > poolSize) {
                               Iterator<Worker> it = workers.iterator();
                               while (it.hasNext() &&
                                               extra-- > 0 &&
                                               currentPoolSize > poolSize)
                                       it.next().stop();
                       }
               } finally {
                       mainLock.unlock();
               }
       }

       /**
        * Returns the number of threads.
        *
        * @return the number of threads
        * @see #setPoolSize
        */
       public int getPoolSize() {
               return poolSize;
       }

       /* Statistics */

       /**
        * Returns the current number of threads in the pool.
        *
        * @return the number of threads
        */
       public int getCurrentPoolSize() {
               return currentPoolSize;
       }

       /**
        * Returns the largest number of threads that have ever
        * simultaneously been in the pool.
        *
        * @return the number of threads
        */
       public int getLargestPoolSize() {
               final ReentrantLock mainLock = this.mainLock;
               mainLock.lock();
               try {
                       return largestPoolSize;
               } finally {
                       mainLock.unlock();
               }
       }

       /**
        * Method invoked when the thread pool has terminated.  Default
        * implementation does nothing. Note: To properly nest multiple
        * overridings, subclasses should generally invoke
        * <tt>super.terminated</tt> within this method.
        */
       protected void terminated() { }


}
