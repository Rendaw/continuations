package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Matthias Mann
 */
public class NullTest implements SuspendableRunnable {

	Object result = "b";

	@Test
	public void testNull() {
		int count = 0;
		final Coroutine co = new Coroutine(this);
		while (co.getState() != Coroutine.State.FINISHED) {
			++count;
			co.run();
		}
		assertEquals(2, count);
		assertEquals("a", result);
	}

	public void run() throws SuspendExecution {
		result = getProperty();
	}

	private Object getProperty() throws SuspendExecution {
		Object x = null;

		final Object y = getProtery("a");
		if (y != null) {
			x = y;
		}

		return x;
	}

	private Object getProtery(final String string) throws SuspendExecution {
		Coroutine.yield();
		return string;
	}

}
