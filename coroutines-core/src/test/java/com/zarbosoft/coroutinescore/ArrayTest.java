/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.zarbosoft.coroutinescore;

import static org.junit.Assert.assertEquals;

/**
 * @author Matthias Mann
 */
public class ArrayTest implements SuspendableRunnable {

	private static final PatchLevel l1 = new PatchLevel();
	private static final PatchLevel[] l2 = new PatchLevel[] {l1};
	private static final PatchLevel[][] l3 = new PatchLevel[][] {l2};

	public void testArray() {
		final Coroutine co = new Coroutine(this);
		co.run();
		assertEquals(42, l1.i);
	}

	public void run() throws SuspendExecution {
		final PatchLevel[][] local_patch_levels = l3;
		final PatchLevel patch_level = local_patch_levels[0][0];
		patch_level.setLevel(42);
	}

	public static class PatchLevel {
		int i;

		public void setLevel(final int value) throws SuspendExecution {
			i = value;
		}
	}
}
