/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.zarbosoft.coroutines;

import junit.framework.TestCase;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author mam
 */
public class MergeTest extends TestCase implements CoroutineProto {

	public static void throwsIO() throws IOException {
	}

	public void coExecute() throws SuspendExecution {
		try {
			throwsIO();
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testMerge() {
		final Coroutine c = new Coroutine(new MergeTest());
		c.run();
	}
}
