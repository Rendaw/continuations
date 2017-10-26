package com.zarbosoft.coroutines;

import static org.junit.Assert.assertTrue;

/**
 * @author mam
 */
public class Merge2Test implements CoroutineProto {

	public interface Interface {
		public void method();
	}

	public static Interface getInterface() {
		return null;
	}

	public static void suspendable() throws SuspendExecution {
	}

	public void run() throws SuspendExecution {
		try {
			final Interface iface = getInterface();
			iface.method();
		} catch (final IllegalStateException ise) {
			suspendable();
		}
	}

	@org.junit.Test
	public void testMerge2() {
		try {
			final Coroutine c = new Coroutine(new Merge2Test());
			c.run();
			assertTrue("Should not reach here", false);
		} catch (final NullPointerException ex) {
			// NPE expected
		}
	}
}
