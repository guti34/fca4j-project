/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package fr.lirmm.fca4j.main;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;

import fr.lirmm.fca4j.command.AOCPosetBuilder;
import fr.lirmm.fca4j.command.Binarizer;
import fr.lirmm.fca4j.command.Clarifier;
import fr.lirmm.fca4j.command.Command;
import fr.lirmm.fca4j.command.Conversion;
import fr.lirmm.fca4j.command.FamilyCommand;
import fr.lirmm.fca4j.command.Inspect;
import fr.lirmm.fca4j.command.Irreductible;
import fr.lirmm.fca4j.command.LatticeBuilder;
import fr.lirmm.fca4j.command.RCACommand;
import fr.lirmm.fca4j.command.Reducer;
import fr.lirmm.fca4j.command.RuleBasisBuilder;
import fr.lirmm.fca4j.iset.AbstractSetContext;

/**
 * The Class Main.
 */
public class Main {
	
	/** The commands. */
	static Command[] commands;
	
	/** The command. */
	static Command command = null;
	
	/** The timeout. */
	static long timeout = -1L;

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public final static void main(String[] args) {
		AbstractSetContext setContext = new SetContextComplete();
		commands = new Command[] { new LatticeBuilder(setContext), new AOCPosetBuilder(setContext),
				new RuleBasisBuilder(setContext), new Clarifier(setContext), new Reducer(setContext),
				new Inspect(setContext), new Irreductible(setContext), new Binarizer(setContext),
				new Conversion(setContext), new FamilyCommand(setContext), new RCACommand(setContext) };

		boolean help = false;
		for (String arg : args) {
			if (!arg.startsWith("-")) {
				if ("help".equalsIgnoreCase(arg))
					help = true;
				else {
					for (Command cmd : commands) {
						if (cmd.name().equalsIgnoreCase(arg)) {
							if (command != null) {
								printHelp(command, true);
								return;
							} else {
								command = cmd;
							}

						}

					}
				}
			}
		}

		if (command == null) {
			printHelp(true);
			return;
		} else {
			if (help) {
				printHelp(command, true);
				return;
			}
		}
		// create the parser
		CommandLineParser parser = new DefaultParser();
		// parse the command line arguments
		try {
			CommandLine line = parser.parse(command.getOptions(), args, false);
			command.checkOptions(line);
			if (line.hasOption("z"))
				timeout = Long.parseLong(line.getOptionValue("z"));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
			System.out.println("\nTry \"help " + command.name() + "\" to get help on command syntax\n");
			return;
		}
		Runnable task = new Runnable() {
			public void run() {
				try {
					command.exec();
				} catch (Exception e) {
					e.printStackTrace();
//					System.out.println(e.getClass().getName() + ": " + e.getMessage());
					System.out.println("Abort during algorithm execution\n");
				}
			}
		};
		if (timeout > 0) {
			ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
			Future future = executor.submit(task);
			executor.schedule(new Runnable() {
				public void run() {
					System.out.println("timeout= " + timeout + "s");
					future.cancel(true);
				}
			}, timeout, TimeUnit.SECONDS);
			executor.shutdown();
		} else
			task.run();
	}

	/**
	 * Gets the command.
	 *
	 * @param pcmd command name
	 * @return command
	 */
	private static Command getCommand(String pcmd) {
		for (Command cmd : commands)
			if (pcmd.equalsIgnoreCase(cmd.name()))
				return cmd;
		return null;
	}

	/**
	 * Prints the header.
	 *
	 * @param pw the writer
	 */
	static private void printHeader(PrintWriter pw) {
		StringBuffer sb = new StringBuffer();
		String title = "FCA4J Command Line Interface " + Main.class.getPackage().getImplementationVersion();
		for (int i = 0; i < title.length(); i++)
			sb.append("*");
		pw.println(sb.toString() + "\n" + title + "\n" + sb.toString() + "\n");
	}

	/**
	 * Prints the help.
	 *
	 * @param cmd the command
	 * @param printHeader enable print header
	 */
	static void printHelp(Command cmd, boolean printHeader) {
		if (cmd == null) {
			printHelp(printHeader);
			return;
		}
		PrintWriter pw = new PrintWriter(System.out);
		if (printHeader)
			printHeader(pw);
		HelpFormatter formatter = new HelpFormatter();
		String s = "command: " + cmd.name().toUpperCase() + "\n\ndescription:\t" + cmd.description() + "\n\n";
		formatter.printWrapped(pw, HelpFormatter.DEFAULT_WIDTH, 5, s);
		formatter.setOptionComparator(null);
		formatter.printHelp(
				pw, HelpFormatter.DEFAULT_WIDTH, "java -jar fca4j-cli.jar " + cmd.name().toUpperCase() + " <"
						+ cmd.getArgName1() + "> [<" + cmd.getArgName2() + ">] [options]\n\noptions:\n",
				"", cmd.getOptions(), 5, 0, "", false);

		// examples
		List<String[]> examples = cmd.examples();
		if (!examples.isEmpty()) {
			formatter.printWrapped(pw, HelpFormatter.DEFAULT_WIDTH, "\nexamples:\n\n");
			for (String[] example : examples) {
				formatter.printWrapped(pw, 2 * HelpFormatter.DEFAULT_WIDTH,
						"java -jar fca4j-cli.jar " + cmd.name().toUpperCase() + " " + example[1] + "\n");
				formatter.printWrapped(pw, HelpFormatter.DEFAULT_WIDTH, 10, "    means:" + example[2] + "\n\n");
			}
		}
		pw.flush();
	}

	/**
	 * Prints the help.
	 *
	 * @param printHeader enable print header
	 */
	static private void printHelp(boolean printHeader) {
		PrintWriter pw = new PrintWriter(System.out);
		if (printHeader)
			printHeader(pw);
		HelpFormatter formatter = new HelpFormatter();
		formatter.setOptionComparator(null);
		formatter.printUsage(pw, 74,
				"java -jar fca4j-cli.jar <command> <input> [<output>] [options]\n\navailable commands are:\n\n");
		String text = "";
		for (Command cmd : commands) {
			text += cmd.name() + "\t" + cmd.description() + "\n\n";
		}
		text += "help <command>" + "\t" + "print help to read more about a command\n\n";
		formatter.printWrapped(pw, HelpFormatter.DEFAULT_WIDTH, 20, text);
		pw.flush();
	}
}
