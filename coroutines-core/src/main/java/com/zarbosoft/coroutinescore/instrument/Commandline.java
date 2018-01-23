package com.zarbosoft.coroutinescore.instrument;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;

import java.io.File;

public class Commandline {
	public static void main(final String[] args) {
		final Project project = new Project();
		for (final String arg : args) {
			final InstrumentationTask task = new InstrumentationTask();
			task.setProject(project);
			final FileSet files = new FileSet();
			files.setProject(project);
			files.setFile(new File(arg));
			task.addFileSet(files);
			task.execute();
		}
	}
}
