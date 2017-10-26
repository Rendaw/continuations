/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.zarbosoft.coroutines;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * @author mam
 */
public class InheritTest {

	@Test
	public void testInherit() {
		final C dut = new C();
		final Coroutine c = new Coroutine(new CoroutineProto() {
			public void run() throws SuspendExecution {
				dut.myMethod();
			}
		});
		for (int i = 0; i < 3; i++) {
			c.run();
		}

		assertEquals(5, dut.result.size());
		assertEquals("a", dut.result.get(0));
		assertEquals("o1", dut.result.get(1));
		assertEquals("o2", dut.result.get(2));
		assertEquals("b", dut.result.get(3));
		assertEquals("b", dut.result.get(4));
	}

	public static class A {
		public static void yield() throws SuspendExecution {
			Coroutine.yield();
		}
	}

	public static class B extends A {
		final ArrayList<String> result = new ArrayList<>();
	}

	public static class C extends B {

		public void otherMethod() throws SuspendExecution {
			result.add("o1");
			Coroutine.yield();
			result.add("o2");
		}

		public void myMethod() throws SuspendExecution {
			result.add("a");
			otherMethod();

			for (; ; ) {
				result.add("b");
				if (result.size() > 10) {
					otherMethod();
					result.add("Ohh!");
				}
				yield();
			}
		}
	}
}
