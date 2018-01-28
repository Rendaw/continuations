package com.zarbosoft.coroutinescore;

import org.junit.Test;

public class InstrumentationTest2 {

	@Test
	public void dummy() {

	}

	public static void method() throws SuspendExecution {
		// Exception table range adjustment issue - finally must not be empty, no statements after yield
		// Extra statements to pad ranges for reading ease
		try {
			if (true)
				throw new RuntimeException();
		} catch (final RuntimeException e) {
			final int z = 0;
			final int y = 0;
			final int x = 0;
			final int w = 0;
			final int v = 0;
			Coroutine.yield();
		} finally {
			final int a = 0;
			final int b = 0;
			final int c = 0;
			final int d = 0;
			final int e = 0;
		}
	}
}
