/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.zarbosoft.coroutines;

import junit.framework.TestCase;
import org.junit.Test;

import java.io.*;
import java.util.Iterator;

/**
 * @author Matthias Mann
 */
public class SerializeTest extends TestCase {

	@Test
	public void testSerialize() throws IOException, ClassNotFoundException {
		final Iterator<String> iter1 = new TestIterator();

		assertEquals("A", iter1.next());
		assertEquals("B", iter1.next());
		assertEquals("C0", iter1.next());
		assertEquals("C1", iter1.next());

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(iter1);
		oos.close();

		final byte[] bytes = baos.toByteArray();

		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final ObjectInputStream ois = new ObjectInputStream(bais);
		final Iterator<String> iter2 = (Iterator<String>) ois.readObject();

		assertNotSame(iter1, iter2);

		assertEquals("C2", iter2.next());
		assertEquals("C3", iter2.next());
		assertEquals("D", iter2.next());
		assertEquals("E", iter2.next());
		assertFalse(iter2.hasNext());

		assertEquals("C2", iter1.next());
		assertEquals("C3", iter1.next());
		assertEquals("D", iter1.next());
		assertEquals("E", iter1.next());
		assertFalse(iter1.hasNext());
	}

	private static class TestIterator extends CoIterator<String> implements Serializable {
		@Override
		protected void run() throws SuspendExecution {
			produce("A");
			produce("B");
			for (int i = 0; i < 4; i++) {
				produce("C" + i);
			}
			produce("D");
			produce("E");
		}
	}
}
