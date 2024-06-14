package org.jcyclone.core.internal;

import org.jcyclone.core.cfg.ISystemConfig;
import org.jcyclone.core.handler.IEventHandler;
import org.jcyclone.core.handler.ISingleThreadedEventHandler;
import org.jcyclone.core.queue.ISource;
import org.jcyclone.core.rtc.IResponseTimeController;
import org.jcyclone.core.stage.IStageManager;

import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread-per-stage scheduler
 * Utilizes the {@see java.util.concurrent} package introduced in Java 1.5
 * 
 * @author Graham Miller
 * @version $Id: TPSSchedulerConcurrent.java,v 1.1 2006/09/27 01:37:23 tolikuznets Exp $
 */
public class TPSSchedulerConcurrent implements IScheduler {

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_VERBOSE = false;

    protected IStageManager mgr;
    protected ISystemConfig config;
    protected Hashtable<IStageWrapper, StageRunnable> stageWrapperTable;     // IStageWrapper --> StageRunnable
    protected boolean crashOnException;

    public TPSSchedulerConcurrent(IStageManager mgr) {
        this(mgr, true);
    }

    public TPSSchedulerConcurrent(IStageManager mgr, boolean initialize) {
        this.mgr = mgr;
        this.config = mgr.getConfig();

        if (initialize) {
            stageWrapperTable = new Hashtable<IStageWrapper, StageRunnable>();
        }

        crashOnException = config.getBoolean("global.crashOnException");
    }

    /**
     * Register a stage with this thread manager.
     */
    public synchronized void register(IStageWrapper stage) {
        if (stageWrapperTable.contains(stage)) {
            throw new IllegalStateException("Stage " + stage.getStage().getName() + " already registered");
        }
        // Create a threadPool for the stage
        StageRunnable sr = new StageRunnable(stage);
        stageWrapperTable.put(stage, sr);
        sr.start();
    }

    /**
     * Deregister a stage with this thread manager.
     */
    public synchronized void deregister(IStageWrapper stage) {
        StageRunnable sr = (StageRunnable) stageWrapperTable.get(stage);
        if (sr == null) {
            //ignore - we've already been deregistered
            return;
        }
        sr.shutdown();
        stageWrapperTable.remove(stage);
    }

    /**
     * Stop the thread manager and all threads managed by it.
     */
    public synchronized void deregisterAll() {
        Set<IStageWrapper> theKeys = new HashSet<IStageWrapper>(stageWrapperTable.keySet());
        for (IStageWrapper wrapper : theKeys) {
            StageRunnable sr = stageWrapperTable.get(wrapper);
            sr.shutdown();
            stageWrapperTable.remove(wrapper);
        }
    }

    /**
     * Wake any thread waiting for work.  This is called by
     * an enqueue* method of FiniteQueue.
     */
    public void wake() { /* do nothing*/
    }

    /**
     * Internal class representing the Runnable for a single stage.
     */
    protected class StageRunnable extends Thread {

        protected ThreadPoolExecutor tp;
        protected IStageWrapper wrapper;
        protected IBatchSorter sorter;
        protected IEventHandler handler;
        protected ISource source;
        protected String name;
        protected IResponseTimeController rtController = null;
        protected boolean firstToken = false;
        protected int blockTime = -1;
        protected int terminationTimeout = 100;


        protected StageRunnable(IStageWrapper wrapper) {
            this.wrapper = wrapper;
            // Create a threadPool for the stage
            this.init();
        }

        private void init() {
            this.source = wrapper.getSource();
            this.handler = wrapper.getEventHandler();
            this.name = wrapper.getStage().getName();
            this.rtController = wrapper.getResponseTimeController();

            this.sorter = wrapper.getBatchSorter();
            if (this.sorter == null) {
                // XXX MDW: Should be ControlledBatchSorter
                this.sorter = new NullBatchSorter();
            }
            sorter.init(wrapper, mgr);

            ISystemConfig config = mgr.getConfig();

            // First look for stages.[stageName] options, then global options
            String tag = "stages." + (wrapper.getStage().getName()) + ".threadPool.";
            String globaltag = "global.threadPool.";

            int initialThreads = config.getInt(tag + "initialThreads");
            if (initialThreads < 1) {
                initialThreads = config.getInt(globaltag + "initialThreads");
                if (initialThreads < 1) initialThreads = 1;
            }
            int minThreads = config.getInt(tag + "minThreads");
            if (minThreads < 1) {
                minThreads = config.getInt(globaltag + "minThreads");
                if (minThreads < 1) minThreads = 1;
            }
            int maxThreads = config.getInt(tag + "maxThreads", 0);
            if (maxThreads == 0) {
                maxThreads = config.getInt(globaltag + "maxThreads", 0);
                if (maxThreads == 0) maxThreads = Integer.MAX_VALUE; // Infinite
            }

            int blockTime = config.getInt(tag + "blockTime",
                config.getInt(globaltag + "blockTime", 1000));
            int idleTimeThreshold = config.getInt(tag + "sizeController.idleTimeThreshold",
                config.getInt(globaltag + "sizeController.idleTimeThreshold", blockTime));

            if (wrapper.getEventHandler() instanceof ISingleThreadedEventHandler) {
                tp = new ThreadPoolExecutor(1,1,idleTimeThreshold, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
            } else {
                tp = new ThreadPoolExecutor(initialThreads,maxThreads,idleTimeThreshold, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
            }

        }

        public void shutdown() {
            tp.shutdown();
            boolean terminated = false;
            while (true) {
                try {
                    terminated = tp.awaitTermination(terminationTimeout, TimeUnit.MILLISECONDS);
                    if (terminated) {
                        break;
                    } else {
                        tp.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    tp.shutdownNow();
                }
            }
        }

        public void run() {
            if (DEBUG) System.err.println(name + ": starting, source is " + source);

            while (!tp.isShutdown() && !tp.isTerminated() && !tp.isTerminating()) {

                try {
                    if (DEBUG_VERBOSE) System.err.println(name + ": Doing blocking dequeue for " + wrapper);

                    // todo: is this necessary?
                    Thread.yield(); // only accomplishes delay

                    // Run any pending batches
                    IBatchDescr batch;

                    while ((batch = sorter.nextBatch(blockTime)) != null) {
                        final List events = batch.getBatch();
                        if (DEBUG_VERBOSE) System.err.println("<" + name + ">: Got batch of " + events.size() + " events");

                        // Call event handler
                        Runnable eventRunner = new EventRunnable(events, batch);
                        tp.execute(eventRunner);

                    }

                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        /** This is a representation of an execution unit */
        private class EventRunnable implements Runnable {
            private final List events;
            private final IBatchDescr batch;    // pointer to a parent batch of events

            public EventRunnable(List events, IBatchDescr outerBatch) {
                this.events = events;
                this.batch = outerBatch;
            }

            public void run() {
                try {
                    long tstart;
                    long tend;
                    tstart = System.currentTimeMillis();
                    handler.handleEvents(events);
                    batch.batchDone();
                    tend = System.currentTimeMillis();

                    // Record service rate
                    wrapper.getStats().recordServiceRate(events.size(), tend - tstart);
                    // Run response time controller
                    if (rtController != null) {
                        rtController.adjustThreshold(events, tend - tstart);
                    }

                } catch (Exception e) {
                    System.err.println("JCyclone: Stage <" + name + "> got exception: " + e);
                    e.printStackTrace();
                    if (crashOnException) {
                        System.err.println("JCyclone: Crashing runtime due to exception - goodbye");
                        System.exit(-1);
                    }
                }
            }
        }
    }

}
