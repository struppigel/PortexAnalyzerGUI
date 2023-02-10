package com.github.struppigel.gui.utils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Singleton that holds and cancels SwingWorkers when new PE loaded. Long running workers will otherwise set data for
 * previously loaded files.
 */
public class WorkerKiller {

    private static WorkerKiller singleton;

    private List<SwingWorker> workers = new ArrayList();

    private WorkerKiller(){}

    public static synchronized WorkerKiller getInstance() {
        if(singleton == null) {
            singleton = new WorkerKiller();
        }
        return singleton;
    }

    public void addWorker(SwingWorker worker) {
        cleanDoneWorkers(); // use this occasion to get rid of old ones
        this.workers.add(worker);
    }

    private void cleanDoneWorkers() {
        List<SwingWorker> toRemove = workers.stream().filter(w -> w.isCancelled() || w.isDone()).collect(Collectors.toList());
        for(SwingWorker w : toRemove) {
            workers.remove(w);
        }
    }

    public void cancelAndDeleteWorkers() {
        for(SwingWorker worker : workers) {
            worker.cancel(true);
        }
        workers.clear();
    }
}
