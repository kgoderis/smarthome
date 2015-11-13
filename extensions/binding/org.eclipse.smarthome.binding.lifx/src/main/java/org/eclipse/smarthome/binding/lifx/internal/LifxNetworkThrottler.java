package org.eclipse.smarthome.binding.lifx.internal;

import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifxNetworkThrottler {

    private static Logger logger = LoggerFactory.getLogger(LifxNetworkThrottler.class);

    private static ReentrantLock networkLock = new ReentrantLock();

    public static void lockNetwork() {
        networkLock.lock();
    }

    public static void unlockNetwork() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            logger.error("An exception occurred while putting the thread to sleep : '{}'", e.getMessage());
        } finally {
            networkLock.unlock();
        }
    }

}
