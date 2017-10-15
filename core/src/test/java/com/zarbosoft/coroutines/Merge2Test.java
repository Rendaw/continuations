/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.zarbosoft.coroutines;

import junit.framework.TestCase;

/**
 * @author mam
 */
public class Merge2Test extends TestCase implements CoroutineProto {

	public interface Interface {
		public void method();
	}

	public static Interface getInterface() {
		return null;
	}

	public static void suspendable() throws SuspendExecution {
	}

	public void coExecute() throws SuspendExecution {
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
