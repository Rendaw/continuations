/*
 * Copyright (c) 2008-2013, Matthias Mann
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.zarbosoft.coroutinescore;

import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class SuspendConstructorArgumentTest {

	public static int helper() throws SuspendExecution {
		Coroutine.yield();
		return 9999;
	}

	@Test
	public void test1Argument() throws IOException {
		class Target {
			Target(final int arg) {
			}
		}
		class TestCoroutineProto implements SuspendableRunnable {
			int value = 0;

			@Override
			public void run() throws SuspendExecution {
				value = 1;
				new Target(helper());
				value = 2;
			}
		}
		final TestCoroutineProto proto = new TestCoroutineProto();
		final Coroutine coroutine = new Coroutine(proto);
		coroutine.run();
		assertThat(proto.value, equalTo(1));
		coroutine.run();
		assertThat(proto.value, equalTo(2));
	}

	@Test
	public void test2Argument() throws IOException {
		class Target {
			Target(final int arg, final int arg2) {
			}
		}
		class TestCoroutineProto implements SuspendableRunnable {
			int value = 0;

			@Override
			public void run() throws SuspendExecution {
				value = 1;
				new Target(helper(), helper());
				value = 2;
			}
		}
		final TestCoroutineProto proto = new TestCoroutineProto();
		final Coroutine coroutine = new Coroutine(proto);
		coroutine.run();
		assertThat(proto.value, equalTo(1));
		coroutine.run();
		assertThat(proto.value, equalTo(1));
		coroutine.run();
		assertThat(proto.value, equalTo(2));
	}
}
