package com.zarbosoft.coroutinescore;

/**
 * Like Runnable but can suspend.
 */
@FunctionalInterface
public interface SuspendableRunnable {
	void run() throws SuspendExecution;
}
