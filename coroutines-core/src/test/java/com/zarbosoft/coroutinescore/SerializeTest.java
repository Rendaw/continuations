/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.zarbosoft.coroutinescore;

import org.junit.Test;

import java.io.*;

import static com.zarbosoft.coroutinescore.Coroutine.State.FINISHED;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;

public class SerializeTest {
	static class TestCoroutineProto implements CoroutineProto, Serializable {
		private static final long serialVersionUID = 351278561540L;
		int value = 0;

		@Override
		public void run() throws SuspendExecution {
			value = 1;
			Coroutine.yield();
			value = 2;
		}
	}

	@Test
	public void testSerialize() throws IOException, ClassNotFoundException {

		final TestCoroutineProto proto = new TestCoroutineProto();
		final Coroutine coroutine = new Coroutine(proto);

		coroutine.run();
		assertThat(proto.value, equalTo(1));
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		{
			final ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(coroutine);
			oos.close();
		}
		final Coroutine coroutine2;
		{
			final byte[] bytes = baos.toByteArray();
			final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
			final ObjectInputStream ois = new ObjectInputStream(bais);
			coroutine2 = (Coroutine) ois.readObject();
		}

		assertNotSame(coroutine, coroutine2);
		assertNotSame(coroutine.getProto(), coroutine2.getProto());

		coroutine2.run();
		assertThat(((TestCoroutineProto) coroutine2.getProto()).value, equalTo(2));
		assertThat(coroutine2.getState(), equalTo(FINISHED));
	}
}
