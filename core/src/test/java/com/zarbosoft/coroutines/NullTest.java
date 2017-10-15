/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.zarbosoft.coroutines;

import junit.framework.TestCase;
import org.junit.Test;

/**
 * @author Matthias Mann
 */
public class NullTest extends TestCase implements CoroutineProto {

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

	public void coExecute() throws SuspendExecution {
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
