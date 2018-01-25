package com.zarbosoft.coroutinescore;

import org.junit.Test;

import static com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class NestingTest {
	@Test
	public void testSuspend() throws NoSuchMethodException {
		final int[] value = new int[] {0};
		final Coroutine co = new Coroutine(new SuspendableRunnable() {
			public final void run() throws SuspendExecution {
				final Coroutine co2 = new Coroutine(new SuspendableRunnable() {
					public final void run() throws SuspendExecution {
						value[0] = 2;
						Coroutine.yield();
						value[0] = 3;
					}
				});
				value[0] = 1;
				Coroutine.yield();
				co2.run();
				assertThat(value[0], equalTo(2));
				co2.run();
				assertThat(value[0], equalTo(3));
				Coroutine.yield();
				value[0] = 4;
				assertThat(value[0], equalTo(4));
			}
		});
		co.run();
		assertThat(value[0], equalTo(1));
		co.run();
		assertThat(value[0], equalTo(3));
		co.run();
		assertThat(value[0], equalTo(4));
		assertThat(co.getState(), equalTo(FINISHED));
	}

}