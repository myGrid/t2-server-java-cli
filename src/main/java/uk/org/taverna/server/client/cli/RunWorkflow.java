/*
 * Copyright (c) 2010-2012 The University of Manchester, UK.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the names of The University of Manchester nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
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

package uk.org.taverna.server.client.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.io.FileUtils;

import uk.org.taverna.server.client.InputPort;
import uk.org.taverna.server.client.OutputPort;
import uk.org.taverna.server.client.Run;
import uk.org.taverna.server.client.RunInputsNotSetException;
import uk.org.taverna.server.client.RunStatus;
import uk.org.taverna.server.client.Server;
import uk.org.taverna.server.client.connection.UserCredentials;

/**
 * 
 * @author Robert Haines
 * 
 */
public final class RunWorkflow extends ConsoleApp {

	private static final String NAME = "RunWorkflow";

	public RunWorkflow() {
		super(NAME);
	}

	@Override
	public void run(CommandLine line) {

		// load workflow
		byte[] workflow = getWorkflow(line);

		// parse inputs
		Map<String, String> inputs = getInputs(line);
		Map<String, File> files = getInputFiles(line);

		boolean outputRefs = false;
		if (line.hasOption('r')) {
			outputRefs = true;
		}

		boolean deleteRun = false;
		if (line.hasOption('D')) {
			deleteRun = true;
		}

		File baclavaIn = null;
		if (line.hasOption('b')) {
			baclavaIn = new File(line.getOptionValue('b'));
		}

		File baclavaOut = null;
		if (line.hasOption('o')) {
			baclavaOut = new File(line.getOptionValue('o', "out.xml"));
		}

		// get server address from left over arguments
		Server server = getServer(line.getArgs());
		UserCredentials credentials = getCredentials();

		// create run
		Run run = server.createRun(workflow, credentials);
		System.out.println("Created run with id: " + run.getIdentifier());
		System.out.println("Created at " + run.getCreateTime());

		// set inputs
		if (baclavaIn != null) {
			try {
				run.setBaclavaInput(baclavaIn);
				System.out.println("Uploaded baclava input file");
			} catch (IOException e) {
				System.out.println(e);
			}
		} else {
			Map<String, InputPort> ports = run.getInputPorts();
			for (String name : ports.keySet()) {
				if (inputs.containsKey(name)) {
					String value = inputs.get(name);
					ports.get(name).setValue(value);
					System.out.format("Set input '%s' to '%s'\n", name, value);
				} else if (files.containsKey(name)) {
					File file = files.get(name);
					ports.get(name).setFile(file);
					System.out.format(
							"Set input '%s' to use file '%s' as input\n", name,
							file.getName());
				}
			}
		}

		// output baclava?
		if (baclavaOut != null) {
			run.requestBaclavaOutput();
		}

		// Start run and wait until it is finished
		try {
			run.start();
		} catch (IOException e) {
			System.out.println("One of the files you set as an input could "
					+ "not be read. Full error is:\n" + e.getMessage());
			run.delete();
			System.exit(1);
		} catch (RunInputsNotSetException e) {
			System.out.println("At least one input has not been set:");
			for (String name : e.getInputNames()) {
				System.out.println(" - " + name);
			}
			run.delete();
			System.exit(1);
		}

		System.out.println("Started at " + run.getStartTime());
		System.out.print("Running");
		while (run.getStatus() == RunStatus.RUNNING) {
			try {
				Thread.sleep(1000);
				System.out.print(".");
			} catch (InterruptedException e) {
			}
		}
		System.out.println("\nFinished at " + run.getFinishTime());

		// get outputs
		String stdout = run.getConsoleOutput();
		String stderr = run.getConsoleError();
		int exitcode = run.getExitCode();
		System.out.println("Exitcode: " + exitcode);
		if (stdout != "") {
			System.out.println("Stdout:\n" + stdout);
		}
		if (stderr != "") {
			System.out.println("Stderr:\n" + stderr);
		}

		if (exitcode == 0) {
			if (baclavaOut != null) {
				try {
					FileUtils.writeByteArrayToFile(baclavaOut,
							run.getBaclavaOutput());
					System.out.format("Baclava file written to '%s'\n",
							baclavaOut);
				} catch (IOException e) {
					System.out.format("Could not write baclava file '%s'\n",
							baclavaOut.getAbsoluteFile());
				}
			} else {
				System.out.println("Outputs:");
				for (OutputPort port : run.getOutputPorts().values()) {
					System.out.format("          %s (%d) -> ", port.getName(),
							port.getDepth());
					if (outputRefs) {
						System.out.println(port.getValue().getReference());
					} else {
						System.out.println(port);
					}
				}
			}
		}

		// delete run?
		if (deleteRun) {
			run.delete();
			System.out.println("Run deleted");
		}
	}

	private byte[] getWorkflow(CommandLine line) {
		byte[] workflow = null;
		if (line.hasOption('w')) {
			String wkfFilename = line.getOptionValue('w');
			File wkfFile = new File(wkfFilename);
			try {
				workflow = FileUtils.readFileToByteArray(wkfFile);
			} catch (IOException e) {
				System.out.format("Cannot read file '%s'. %s", wkfFilename,
						e.toString());
			}
		}

		// try to read workflow stdin
		if (workflow == null) {
			String wkf = null;
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						System.in));
				while (in.ready()) {
					wkf += in.readLine();
				}
			} catch (IOException e) {
				System.out.println("Cannot read workflow from input stream.");
			}

			workflow = wkf.getBytes();
		}

		// still no workflow?
		if (workflow == null) {
			System.out.println("No workflow provided.");
			showHelpAndExit(1);
		}

		return workflow;
	}

	private Map<String, String> getInputs(CommandLine line) {
		HashMap<String, String> inputs = new HashMap<String, String>();

		if (line.hasOption('i')) {
			String[] pairs = line.getOptionValues('i');

			for (String s : pairs) {
				String[] pair = s.trim().split(":", 2);
				inputs.put(pair[0], pair[1]);
			}
		}

		return inputs;
	}

	private Map<String, File> getInputFiles(CommandLine line) {
		HashMap<String, File> files = new HashMap<String, File>();

		if (line.hasOption('f')) {
			String[] pairs = line.getOptionValues('f');

			for (String s : pairs) {
				String[] pair = s.trim().split(":", 2);
				files.put(pair[0], new File(pair[1]));
			}
		}

		return files;
	}

	@Override
	@SuppressWarnings("static-access")
	public List<Option> registerOptions() {
		ArrayList<Option> opts = new ArrayList<Option>();

		opts.add(OptionBuilder.withLongOpt("baclava-in")
				.withDescription("Set baclava file for input port values")
				.hasArg().withArgName("BACLAVA").create('b'));

		opts.add(OptionBuilder
				.withLongOpt("baclava-out")
				.withDescription(
						"Return outputs in baclava format. A filename may be specified or 'out.xml' is used")
						.hasOptionalArg().withArgName("BACLAVA").create('o'));

		opts.add(OptionBuilder
				.withLongOpt("workflow")
				.withDescription(
						"The workflow to run. If this is not specified then the workflow is read from standard input")
						.hasArg().withArgName("WORKFLOW").create('w'));

		opts.add(OptionBuilder.withLongOpt("input")
				.withDescription("Set input port INPUT to VALUE").hasArg()
				.withArgName("INPUT:VALUE").create('i'));

		opts.add(OptionBuilder
				.withLongOpt("input-file")
				.withDescription(
						"Set input port INPUT to use FILE for its input")
						.hasArg().withArgName("INPUT:FILE").create('f'));

		opts.add(OptionBuilder
				.withLongOpt("output-refs")
				.withDescription(
						"Return URIs that point to the data items of the output rather than the data items themselves.")
						.create('r'));

		opts.add(OptionBuilder
				.withLongOpt("delete")
				.withDescription(
						"Delete the run from the server when it is "
								+ "complete. By default the run and its results are preserved. Note that "
								+ "the run will still be deleted when its expiry time is reached")
								.create('D'));

		return opts;
	}
}
