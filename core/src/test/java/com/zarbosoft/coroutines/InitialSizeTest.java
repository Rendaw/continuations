/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.zarbosoft.coroutines;

import junit.framework.TestCase;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * @author Matthias Mann
 */
public class InitialSizeTest extends TestCase implements CoroutineProto {

	@Test
	public void test1() {
		testWithSize(1);
	}

	@Test
	public void test2() {
		testWithSize(2);
	}

	@Test
	public void test3() {
		testWithSize(3);
	}

	private void testWithSize(final int stackSize) {
		final Coroutine c = new Coroutine(this, stackSize);
		assertEquals(getStackSize(c), stackSize);
		c.run();
		assertEquals(Coroutine.State.SUSPENDED, c.getState());
		c.run();
		assertEquals(Coroutine.State.FINISHED, c.getState());
		assertTrue(getStackSize(c) > 10);
	}

	public void coExecute() throws SuspendExecution {
		assertEquals(3628800, factorial(10));
	}

	private int factorial(final Integer a) throws SuspendExecution {
		if (a == 0) {
			Coroutine.yield();
			return 1;
		}
		return a * factorial(a - 1);
	}

	private int getStackSize(final Coroutine c) {
		try {
			final Field stackField = Coroutine.class.getDeclaredField("stack");
			stackField.setAccessible(true);
			final Object stack = stackField.get(c);
			final Field dataObjectField = Stack.class.getDeclaredField("dataObject");
			dataObjectField.setAccessible(true);
			final Object[] dataObject = (Object[]) dataObjectField.get(stack);
			return dataObject.length;
		} catch (final Throwable ex) {
			throw new AssertionError(ex);
		}
	}
}
