/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Matthias Mann
 */
public class UninitializedTest implements CoroutineProto {

	Object result = "b";

	@Test
	public void testUninitialized() {
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
		final Object x;

		final Object y = getProtery("a");
		if (y != null) {
			x = y;
		} else {
			x = getProtery("c");
		}

		return x;
	}

	private Object getProtery(final String string) throws SuspendExecution {
		Coroutine.yield();
		return string;
	}

}
