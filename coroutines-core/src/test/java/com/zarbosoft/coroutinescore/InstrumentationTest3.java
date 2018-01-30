package com.zarbosoft.coroutinescore;

import org.junit.Test;

import java.io.UncheckedIOException;

public class InstrumentationTest3 {
	@Test
	public void dummy() {

	}

	public static void method() throws SuspendExecution {
		// Exception table range adjustment issue - finally must not be empty, no statements after yield
		try {
			Coroutine.yield();
		} catch (final UncheckedIOException e) {
			return;
		}
	}
}
