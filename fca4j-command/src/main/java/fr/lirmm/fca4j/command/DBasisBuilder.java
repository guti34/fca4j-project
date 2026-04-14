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
package fr.lirmm.fca4j.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import au.com.bytecode.opencsv.CSVWriter;
import fr.lirmm.fca4j.algo.DBaseV18;
import fr.lirmm.fca4j.algo.DBaseV19;
import fr.lirmm.fca4j.algo.DBaseV20;
import fr.lirmm.fca4j.algo.DBaseV23;
import fr.lirmm.fca4j.cli.io.RuleExporter;
import fr.lirmm.fca4j.cli.io.RuleExporters;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

/**
 * The Class RuleBasisBuilder.
 */
public class DBasisBuilder extends Command {

	/** The Constant DEFAULT_MAXTHREAD. */
	public final static int DEFAULT_MAXTHREAD = -1; // depends on available cores

	/** The output file. */
	protected File outputFile;

	/** The input file. */
	protected File inputFile;

	/** The report file. */
	protected File reportFile;

	/** if report exist. */
	protected boolean reportExist = false;

	/** The binary context. */
	protected IBinaryContext ctx;

	/** The algorithm. */
	protected DBaseV23 algo;

	/** The input format. */
	protected ContextFormat inputFormat;

	/** Limit thread usage */
	protected int maxThreads = DEFAULT_MAXTHREAD;

	/** Minimal support */
	protected int minSupport = 0;

	/** threading */
	protected PoolMode poolMode = PoolMode.MULTITHREAD;
	
	/** basis rule export format */
	protected RuleBasisFormat ruleBasisFormat;
	/**
	 * The Enum PoolMode.
	 */
	public enum PoolMode {

		/** The mono thread mode. */
		MONO,
		/** The multitheading mode. */
		MULTITHREAD // (default)
	};

	/**
	 * Instantiates a new rule basis builder.
	 *
	 * @param setContext the set context
	 */
	public DBasisBuilder(ISetContext setContext) {
		super("dbasis", "compute the ordered direct basis of implications (D-Basis)", setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		// specify minimal support
		options.addOption(Option.builder("x").desc("Retain only implications whose support is minimal").hasArg()
				.argName("MINIMAL-SUPPORT").build());
		// multithreading
		options.addOption(Option.builder("t")// .longOpt("multithreading"
				.desc("multithreading options are:\n* MONO \n* MULTITHREAD(default)").hasArg().argName("POOL-MODE")
				.build());
		// output report
		options.addOption(Option.builder("r").desc("generate report about algorithm execution").hasArg()
				.argName("REPORTFILE").build());
		// input format
		declareContextFormat("i", "INPUT-FORMAT");
		// output format
		declareRuleBasisFormat("o", "OUTPUT-FORMAT");

		// implementation
		declareImplementation(true);
		// common options
		declareCommon();
	}

	/**
	 * Check options.
	 *
	 * @param line the command line
	 * @throws Exception the exception
	 */
	public void checkOptions(CommandLine line) throws Exception {
		List<String> args = line.getArgList();
		// System.out.println(args);
		for (String arg : args) {
			if (name().equalsIgnoreCase(arg)) {
				args.remove(arg);
				break;
			}
		}
		if (args.size() < 1)
			throw new Exception("input file missing");
		String inputFileName = args.get(0);
		inputFile = new File(inputFileName);
		if (!inputFile.exists())
			throw new Exception("the specified input file path is not found: " + inputFileName);
		inputFormat = checkContextFormat(line, inputFileName, "i");
		String outputFileName = null;
		if (args.size() > 1)
			outputFileName = args.get(1);
		if (outputFileName != null) {
			outputFile = new File(outputFileName);
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			} else if (!outputFile.canWrite())
				throw new Exception("the specified output file path for the result is not writable !");
		} else
			outputFile = null;
		checkImplementation(line);
		// thread mode
		if (line.hasOption("t")) {
			try {
				poolMode = PoolMode.valueOf(line.getOptionValue("t").toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (poolMode == null)
				throw new Exception("not recognized thread pool mode: " + line.getOptionValue("t"));
		} else
			poolMode = PoolMode.MULTITHREAD;
		// report
		if (line.hasOption("r")) {
			reportFile = new File(line.getOptionValue("r"));
			if (!reportFile.exists()) {
				reportFile.createNewFile();
			} else if (!reportFile.canWrite())
				throw new Exception("the specified report file path is not writable !");
			else
				reportExist = true;
		} else
			reportFile = null;
		// min support
		if (line.hasOption("x")) {
			try {
				String minSupportString = line.getOptionValue("x");
				minSupport = Integer.parseInt(minSupportString);
				if (minSupport < 0)
					throw new Exception();
			} catch (Exception e) {
				throw new Exception("invalid parameter for minimal support  (-x option) specify a positive integer");
			}
		}
		// output format
		ruleBasisFormat=checkRuleBasisFormat(line, outputFileName,"o");
		// separator
		checkSeparator(line);
		// verbose
		checkVerbose(line);
	}

	/**
	 * Prints by support.
	 *
	 * @param folder       the folder
	 * @param implications the implications
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void printBySupport(File folder, List<Implication> implications) throws IOException {
		TreeMap<Integer, List<Implication>> map = new TreeMap<>();

		for (Implication implication : implications) {
			List<Implication> list = map.get(implication.getSupport().cardinality());
			if (list == null) {
				list = new ArrayList<>();
				map.put(implication.getSupport().cardinality(), list);
			}
			list.add(implication);
		}
		for (int support : map.keySet()) {
			FileWriter fileWriter = new FileWriter(
					folder.getPath() + File.separator + ctx.getName() + support + "Rules.txt");
			PrintWriter printWriter = new PrintWriter(fileWriter);
			// output results
			RuleExporter exporter = RuleExporters.fromFormat(ruleBasisFormat.name());
			exporter.export(printWriter, map.get(support), ctx);
			printWriter.flush();
			printWriter.close();
		}
/*		StringBuilder sb = new StringBuilder();
		for (int support : map.keySet()) {
			sb.append(String.format("support %d: %d rules\n", support, map.get(support).size()));
		}
*/		
	}

	/**
	 * Prints the report.
	 *
	 * @param implications    the implications
	 * @param algo            the algorithm
	 * @param closureStrategy the closure strategy
	 * @param chrono          the chrono
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void printReport(List<Implication> implications, Chrono chrono) throws IOException {
		if(separator=='?') separator=',';
		CSVWriter writer = new CSVWriter(new FileWriter(reportFile, reportExist), separator);
		ArrayList<String> keys = new ArrayList<>();
		ArrayList<String> values = new ArrayList<>();
		keys.add("source");
		values.add(inputFile.getName());
		keys.add("nb_objects");
		values.add("" + ctx.getObjectCount());
		keys.add("nb_attributes");
		values.add("" + ctx.getAttributeCount());
		keys.add("incidence");
		values.add("" + ctx.getIncidence());
		keys.add("timestamp");
		values.add("" + new Timestamp(System.currentTimeMillis()).getTime());
		keys.add("java.version");
		values.add(System.getProperty("java.version"));
		keys.add("java.vm.name");
		values.add(System.getProperty("java.vm.name"));
		keys.add("os.arch");
		values.add(System.getProperty("os.arch"));
		keys.add("os.name");
		values.add(System.getProperty("os.name"));
		keys.add("os.version");
		values.add(System.getProperty("os.version"));
		keys.add("nb_core");
		values.add("" + Runtime.getRuntime().availableProcessors());
		keys.add("algo");
		values.add(algo.toString());
		keys.add("pool_mode");
		values.add("" + poolMode);
		keys.add("set_impl");
		values.add(impl.toString());
		keys.add("time_exec");
		values.add("" + chrono.getResult());
		keys.add("nb_implications");
		values.add("" + implications.size());

		ArrayList<String[]> records = new ArrayList<>();
		records.add(keys.toArray(new String[keys.size()]));
		records.add(values.toArray(new String[values.size()]));
		if (!reportExist)
			writer.writeAll(records);
		else
			writer.writeNext(records.get(1));
		writer.flush();
		writer.close();
	}

	/**
	 * Exec.
	 *
	 * @return the resulting object
	 * @throws Exception the exception
	 */
	@Override
	public Object exec() throws Exception {
		ctx = readContext(inputFormat, inputFile);
		if (poolMode == PoolMode.MONO)
			maxThreads = 1;
		algo = new DBaseV23(ctx, minSupport, maxThreads);
		System.out.println("running " + algo + " (" + impl + "/" + poolMode + ") data: " + inputFile.getName() + " ( "
				+ ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		Chrono chrono = new Chrono("dbasis");
		chrono.start(algo.getDescription());
		algo.run();
		chrono.stop(algo.getDescription());
		List<Implication> result = algo.getResult();
		// display info
		if (verbose) {
			System.out.println("total: " + result.size() + "\n");
		}
		System.out.println("duration: " + chrono.getResult(algo.getDescription()) + " ms");
		// output results
		PrintWriter pw;
		if (outputFile != null)
			pw = new PrintWriter(outputFile);
		else
			pw = new PrintWriter(System.out);
		RuleExporter exporter = RuleExporters.fromFormat(ruleBasisFormat.name());
		exporter.export(pw, result, ctx);
		pw.flush();
		pw.close();

		if (reportFile != null)
			printReport(result, chrono);
		return result;
	}

}
