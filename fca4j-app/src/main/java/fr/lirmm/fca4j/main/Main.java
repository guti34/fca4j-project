/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
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
import fr.lirmm.fca4j.command.DBasisBuilder;
import fr.lirmm.fca4j.command.FamilyCommand;
import fr.lirmm.fca4j.command.Inspect;
import fr.lirmm.fca4j.command.Irreductible;
import fr.lirmm.fca4j.command.LatticeBuilder;
import fr.lirmm.fca4j.command.RCACommand;
import fr.lirmm.fca4j.command.Reducer;
import fr.lirmm.fca4j.command.RuleBasisBuilder;
import fr.lirmm.fca4j.command.RCAImport;
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
	static int exitCode=0;

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public final static void main(String[] args) {
		AbstractSetContext setContext = new SetContextComplete();
		commands = new Command[] { new LatticeBuilder(setContext), new AOCPosetBuilder(setContext),
				new RuleBasisBuilder(setContext), new DBasisBuilder(setContext),new Clarifier(setContext), new Reducer(setContext),
				new Inspect(setContext), new Irreductible(setContext), new Binarizer(setContext),
				new Conversion(setContext), new FamilyCommand(setContext), new RCACommand(setContext),new RCAImport(setContext) };

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
		    if (line.hasOption("timeout"))                    // était "z" — ne matchait jamais
		        timeout = Long.parseLong(line.getOptionValue("timeout"));
		} catch (Exception e) {
		    e.printStackTrace();
		    System.out.println(e.getClass().getName() + ": " + e.getMessage());
		    System.out.println("\nTry \"help " + command.name() + "\" to get help on command syntax\n");
		    exitCode = 1;
		    return;
		}

		Runnable task = () -> {
		    try {
		        command.exec();
		    } catch (Exception e) {
		        e.printStackTrace();
		        System.out.println("Abort during algorithm execution\n");
		        exitCode = 1;
		    }
		};

		if (timeout > 0) {
		    Thread worker = new Thread(task, "fca4j-algo");
		    worker.start();
		    try {
		        worker.join(timeout * 1000L);
		    } catch (InterruptedException ie) {
		        Thread.currentThread().interrupt();
		    }
		    if (worker.isAlive()) {
		        System.out.println("timeout reached (" + timeout + "s) — aborting");
		        System.out.flush();
		        Runtime.getRuntime().halt(2);   // tue la JVM + threads natifs, code ≠ 0
		    }
		} else {
		    task.run();
		}
		System.exit(exitCode);	
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
				pw, HelpFormatter.DEFAULT_WIDTH, "java -jar fca4j.jar " + cmd.name().toUpperCase() + " <"
						+ cmd.getArgName1() + "> [<" + cmd.getArgName2() + ">] [options]\n\noptions:\n",
				"", cmd.getOptions(), 5, 0, "", false);

		// examples
		List<String[]> examples = cmd.examples();
		if (!examples.isEmpty()) {
			formatter.printWrapped(pw, HelpFormatter.DEFAULT_WIDTH, "\nexamples:\n\n");
			for (String[] example : examples) {
				formatter.printWrapped(pw, 2 * HelpFormatter.DEFAULT_WIDTH,
						"java -jar fca4j.jar " + cmd.name().toUpperCase() + " " + example[1] + "\n");
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
				"java -jar fca4j.jar <command> <input> [<output>] [options]\n\navailable commands are:\n\n");
		String text = "";
		for (Command cmd : commands) {
			text += cmd.name() + "\t" + cmd.description() + "\n\n";
		}
		text += "help <command>" + "\t" + "print help to read more about a command\n\n";
		formatter.printWrapped(pw, HelpFormatter.DEFAULT_WIDTH, 20, text);
		pw.flush();
	}
}
