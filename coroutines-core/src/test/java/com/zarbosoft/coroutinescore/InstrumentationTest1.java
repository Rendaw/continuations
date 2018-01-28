package com.zarbosoft.coroutinescore;

import org.junit.Test;

public class InstrumentationTest1 {

	@Test
	public void dummy() {

	}

	public static void method() throws SuspendExecution {
		// Exception table range adjustment issue - finally must not be empty, no statements after yield
		int a = 0;
		try {
			++a;
			++a;
			++a;
			++a;
			++a;
			Coroutine.yield();
		} finally {
			++a;
			++a;
			++a;
			++a;
			++a;
		}
	}
}
