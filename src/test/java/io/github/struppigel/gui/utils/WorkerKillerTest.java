package io.github.struppigel.gui.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

class WorkerKillerTest {

    @AfterEach
    void cleanUp() {
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
    }

    @Test
    void cancelsAllRegisteredWorkers() {
        SwingWorker<Void, Void> first = idleWorker();
        SwingWorker<Void, Void> second = idleWorker();
        WorkerKiller.getInstance().addWorker(first);
        WorkerKiller.getInstance().addWorker(second);

        WorkerKiller.getInstance().cancelAndDeleteWorkers();

        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
    }

    @Test
    void doesNotCancelWorkersAddedAfterwards() {
        WorkerKiller.getInstance().addWorker(idleWorker());
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
        SwingWorker<Void, Void> later = idleWorker();

        WorkerKiller.getInstance().addWorker(later);

        assertFalse(later.isCancelled());
    }

    private static SwingWorker<Void, Void> idleWorker() {
        return new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                return null;
            }
        };
    }
}
