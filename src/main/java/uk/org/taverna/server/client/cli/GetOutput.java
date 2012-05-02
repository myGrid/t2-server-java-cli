/*
 * Copyright (c) 2012 The University of Manchester, UK.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;

import uk.org.taverna.server.client.OutputPort;
import uk.org.taverna.server.client.PortValue;
import uk.org.taverna.server.client.Run;
import uk.org.taverna.server.client.Server;
import uk.org.taverna.server.client.connection.UserCredentials;

/**
 * 
 * @author Robert Haines
 * 
 */
public final class GetOutput extends ConsoleApp {

	private static final String NAME = "GetOutput";
	private static final String USAGE = "run-id";
	private static final String EXTRA_USAGE = "run-id is the id number of "
			+ "the run from which you want to collect outputs.";

	public GetOutput() {
		super(NAME, USAGE, EXTRA_USAGE);
	}

	@Override
	public void run(CommandLine line) {

		// Parse data value coordinates
		Map<String, int[]> outputCoords = getCoords(line);

		boolean outputRefs = false;
		if (line.hasOption('r')) {
			outputRefs = true;
		}

		boolean types = false;
		if (line.hasOption('T')) {
			types = true;
		}

		boolean totalSize = false;
		if (line.hasOption('t')) {
			totalSize = true;
		}

		// Get server address and run id from left over arguments.
		String[] args = line.getArgs();
		Server server = getServer(args);
		UserCredentials credentials = getCredentials();

		if (args.length < 2) {
			showHelpAndExit(1);
		}

		// Just grab the first left over argument that is a UUID.
		Run run = null;
		for (String arg : args) {
			try {
				// We don't store ids as UUIDs but this is what they are so
				// use this fact to help us parse them out of the args.
				UUID.fromString(arg);
				run = server.getRun(arg, credentials);
				break;
			} catch (IllegalArgumentException e) {
				// not a UUID, ignore
			}
		}

		if (run == null) {
			System.out.format("Could not find run");
			System.exit(1);
		}

		Set<String> ports = run.getOutputPorts().keySet();
		for (String name : ports) {
			// Get port and and data coords if we have them
			OutputPort port = run.getOutputPort(name);
			int[] coords = new int[0];
			if (outputCoords.containsKey(name)) {
				coords = outputCoords.get(name);
			}
			PortValue value = port.getValue(coords);

			// Print info
			System.out.println(port.getName());
			if (outputRefs) {
				System.out.format("  Reference: %s\n", value.getReference());
			} else {
				if (value.isError()) {
					System.out.format("  ERROR: %s\n", value.getError());
				} else {
					System.out.format("  Value: %s\n", value.getStringData());
				}
			}

			if (types) {
				System.out.format("  Type: %s\n", value.getDataType());
			}

			if (totalSize) {
				System.out
				.format("  Total size: %d\n", port.getTotalDataSize());
			}
		}
	}

	@Override
	@SuppressWarnings("static-access")
	public List<Option> registerOptions() {
		ArrayList<Option> opts = new ArrayList<Option>();

		opts.add(OptionBuilder.withLongOpt("coords").withDescription("")
				.hasArg().withArgName("OUTPUT:X,Y,...").create());

		opts.add(new Option("r", "refs", false, "Return references to the "
				+ "output data rather than the data itself."));

		opts.add(new Option("t", "total-size", false,
				"Return the total size of the data in the output."));

		opts.add(new Option("T", "types", false,
				"Return the mime types of singleton ports."));

		return opts;
	}

	private Map<String, int[]> getCoords(CommandLine line) {
		HashMap<String, int[]> coords = new HashMap<String, int[]>();

		if (line.hasOption("coords")) {
			String[] pairs = line.getOptionValues("coords");

			for (String s : pairs) {
				String[] pair = s.trim().split(":", 2);
				String[] cs = pair[1].split(",");
				int[] list = new int[cs.length];
				for (int i = 0; i < list.length; i++) {
					list[i] = Integer.parseInt(cs[i]);
				}
				coords.put(pair[0], list);
			}
		}

		return coords;
	}
}
