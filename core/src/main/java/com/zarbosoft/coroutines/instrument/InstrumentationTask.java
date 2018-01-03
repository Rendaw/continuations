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
package com.zarbosoft.coroutines.instrument;

import com.zarbosoft.coroutines.SuspendExecution;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Resource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * <p>Instrumentation ANT task</p>
 * <p>
 * <p>It requires one or more FileSet elements pointing to class files that should
 * be instrumented.</p>
 * <p>Classes that are referenced from the instrumented classes are searched in
 * the classpath of the task. If a referenced class is not found a warning is
 * generated and the instrumentation will result in less efficent code.</p>
 * <p>
 * <p>The following options can be set:<ul>
 * <li>check - default: false<br/>The resulting code is run through a verifier.</li>
 * <li>verbose - default: false<br/>The name of each processed class and all suspendable method calles is displayed.</li>
 * <li>debug - default: false<br/>Prints internal debugging information.</li>
 * <li>allowmonitors - default: false<br/>Allows the use of synchronized statements - this is DANGEROUS !</li>
 * <li>allowblocking - default: false<br/>Allows the use known blocking calls like Thread.sleep, Object.wait etc.</li>
 * </ul></p>
 *
 * @author Matthias Mann
 * @see <a href="http://ant.apache.org/manual/CoreTypes/fileset.html">ANT FileSet</a>
 * @see SuspendExecution
 */
public class InstrumentationTask extends Task {

	private final ArrayList<FileSet> filesets = new ArrayList<>();
	private boolean check;
	private boolean verbose;
	private boolean allowMonitors;
	private boolean allowBlocking;
	private boolean debug;
	private boolean writeClasses = true;

	public void addFileSet(final FileSet fs) {
		filesets.add(fs);
	}

	public void setCheck(final boolean check) {
		this.check = check;
	}

	public void setVerbose(final boolean verbose) {
		this.verbose = verbose;
	}

	public void setAllowMonitors(final boolean allowMonitors) {
		this.allowMonitors = allowMonitors;
	}

	public void setAllowBlocking(final boolean allowBlocking) {
		this.allowBlocking = allowBlocking;
	}

	public void setDebug(final boolean debug) {
		this.debug = debug;
	}

	public void setWriteClasses(final boolean writeClasses) {
		this.writeClasses = writeClasses;
	}

	@Override
	public void execute() throws BuildException {
		final MethodDatabase db = new MethodDatabase(getClass().getClassLoader());

		db.setVerbose(verbose);
		db.setDebug(debug);
		db.setAllowMonitors(allowMonitors);
		db.setAllowBlocking(allowBlocking);
		db.setLog(new Log() {
			public void log(final LogLevel level, final String msg, final Object... args) {
				final int msgLevel;
				switch (level) {
					case DEBUG:
						msgLevel = Project.MSG_INFO;
						break;
					case INFO:
						msgLevel = Project.MSG_INFO;
						break;
					case WARNING:
						msgLevel = Project.MSG_WARN;
						break;
					default:
						throw new AssertionError("Unhandled log level: " + level);
				}
				InstrumentationTask.this.log(level + ": " + String.format(msg, args), msgLevel);
			}

			public void error(final String msg, final Exception ex) {
				InstrumentationTask.this.log("ERROR: " + msg, ex, Project.MSG_ERR);
			}
		});

		try {
			for (final FileSet fs : filesets) {
				for (final Resource resource : new Iterable<Resource>() {
					@Override
					public Iterator<Resource> iterator() {
						return fs.iterator();
					}
				}) {
					db.checkClass(new File(fs.getDir(), resource.getName()));
				}
				/*
				final DirectoryScanner ds = fs.getDirectoryScanner(getProject());
				final String[] includedFiles = ds.getIncludedFiles();

				for (final String filename : includedFiles) {
					if (filename.endsWith(".class")) {
						final File file = new File(fs.getDir(), filename);
						if (file.isFile()) {
							db.checkClass(file);
						} else {
							log("File not found: " + filename);
						}
					}
				}
				*/
			}

			db.log(LogLevel.INFO, "Instrumenting " + db.getWorkList().size() + " classes");

			for (final File f : db.getWorkList()) {
				instrumentClass(db, f);
			}
		} catch (final UnableToInstrumentException ex) {
			log(ex.getMessage());
			throw new BuildException(ex.getMessage(), ex);
		}
	}

	private void instrumentClass(final MethodDatabase db, final File f) {
		db.log(LogLevel.INFO, "Instrumenting class %s", f);

		try {
			ClassReader r;

			final FileInputStream fis = new FileInputStream(f);
			try {
				r = new ClassReader(fis);
			} finally {
				fis.close();
			}

			final ClassWriter cw = new DBClassWriter(db, r);
			final ClassVisitor cv = check ? new CheckClassAdapter(cw) : cw;
			final InstrumentClass ic = new InstrumentClass(cv, db, false);
			r.accept(ic, ClassReader.SKIP_FRAMES);

			final byte[] newClass = cw.toByteArray();

			if (writeClasses) {
				final FileOutputStream fos = new FileOutputStream(f);
				try {
					fos.write(newClass);
				} finally {
					fos.close();
				}
			}
		} catch (final IOException ex) {
			throw new BuildException("Instrumenting file " + f, ex);
		}
	}
}
