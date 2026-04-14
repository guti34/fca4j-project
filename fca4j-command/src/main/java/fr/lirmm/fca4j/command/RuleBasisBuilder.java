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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import au.com.bytecode.opencsv.CSVWriter;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.ClosureDirect;
import fr.lirmm.fca4j.algo.ClosureDirectWithForkJoinPool;
import fr.lirmm.fca4j.algo.ClosureStrategy;
import fr.lirmm.fca4j.algo.ClosureWithHistory;
import fr.lirmm.fca4j.algo.DBaseV18;
import fr.lirmm.fca4j.algo.DBaseV19;
import fr.lirmm.fca4j.algo.DBaseV2;
import fr.lirmm.fca4j.algo.DBaseV20;
import fr.lirmm.fca4j.algo.DBaseV23;
import fr.lirmm.fca4j.algo.LinCbO;
import fr.lirmm.fca4j.algo.LinCbOWithPruning;
import fr.lirmm.fca4j.cli.io.RuleBasisReader;
import fr.lirmm.fca4j.cli.io.RuleExporter;
import fr.lirmm.fca4j.cli.io.RuleExporters;
import fr.lirmm.fca4j.cli.io.SLFReader;
import fr.lirmm.fca4j.command.Command.RuleBasisFormat;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

/**
 * The Class RuleBasisBuilder.
 */
public class RuleBasisBuilder extends Command {

	/** The Constant DEFAULT_THRESHOLD. */
	public final static int DEFAULT_THRESHOLD = 50;

	/** The output file. */
	protected File outputFile;

	/** The input file. */
	protected File inputFile;

	/** The report file. */
	protected File reportFile;

	/** if report exist. */
	protected boolean reportExist = false;

	/** The result folder. */
	protected File resultFolder;

	/** The binary context. */
	protected IBinaryContext ctx;

	/** The closure type. */
	protected ClosureType closureType;

	/** The pool mode. */
	protected PoolMode poolMode;

	/** The algorithm. */
	protected AlgoRuleBasis algo;

	/** for sorted print. */
	protected boolean sortedPrint = true;

	/** The input format. */
	protected ContextFormat inputFormat;

	/** basis rule export format */
	protected RuleBasisFormat ruleBasisFormat;
	
	/** The threshold. */
	protected int threshold = DEFAULT_THRESHOLD;

	/** with clarification. */
	protected boolean withClarification = false;

	/**
	 * The Enum ClosureType.
	 */
	public enum ClosureType {

		/** The basic closure. */
		BASIC,
		/** The closure using history. */
		WITH_HISTORY
	};

	/**
	 * The Enum PoolMode.
	 */
	public enum PoolMode {

		/** The mono thred mode. */
		MONO,
		/** The forkjoinpool mode. */
		FORKJOINPOOL
		// , EXECUTOR_SERVICE
	};

	/**
	 * The Enum AlgoRuleBasis.
	 */
	public enum AlgoRuleBasis {

		/** The lincbo algorithm. */
		LINCBO,
		/** The lincbopruning algorithm. */
		LINCBOPRUNING
	};

	/**
	 * Instantiates a new rule basis builder.
	 *
	 * @param setContext the set context
	 */
	public RuleBasisBuilder(ISetContext setContext) {
		super("rulebasis", "compute the canonical basis of implications (Duquenne-Guigues)", setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		options.addOption(Option.builder("clarify")
				.desc("this option can speed up the execution by basing the calculation on the clarified context")
				.build());
		StringBuilder sb_algo = new StringBuilder();
		for (AlgoRuleBasis algo : AlgoRuleBasis.values()) {
			sb_algo.append("\n* " + algo.name());
		}
		sb_algo.append(" (default)");
		// algo
		options.addOption(
				Option.builder("a").desc("supported algorithms are " + sb_algo).hasArg().argName("ALGO").build());
		// closure
		options.addOption(Option.builder("c")// .longOpt("closure")
				.desc("two methods to perform the closure operation are available\n* BASIC (default)\n* WITH_HISTORY")
				.hasArg().argName("CLOSURE").build());
		// multithreading
		options.addOption(Option.builder("t")// .longOpt("multithreading"
				.desc("multithreading options are:\n* MONO (default)\n* FORKJOINPOOL").hasArg().argName("POOL-MODE")
				.build());
		options.addOption(Option.builder("h").desc("multithreading limit size of work for a task default=50 ").hasArg()
				.argName("THRESHOLD").build());
		options.addOption(Option.builder("b").desc("sort implications by ascending support").build());
		// output report
		options.addOption(Option.builder("r").desc("generate report about algorithm execution").hasArg()
				.argName("REPORTFILE").build());
		// folder output with 1 file by support
		options.addOption(Option.builder("folder").desc("folder to generate a file by support for results").hasArg()
				.argName("PATH").build());
		// input format
		declareContextFormat("i", "INPUT-FORMAT");
		// output format
		declareRuleBasisFormat("o","OUTPUT-FORMAT");

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
		withClarification = line.hasOption("clarify");
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
		if (line.hasOption("folder")) {
			String folderPath = line.getOptionValue("folder");
			resultFolder = new File(folderPath);
			if (!resultFolder.exists()) {
				try {
					if (!resultFolder.mkdirs())
						throw new Exception();
				} catch (Exception e) {
					throw new Exception("folder " + folderPath + " cannot be created. " + e.getMessage());
				}
			}
			if (!resultFolder.isDirectory()) {
				throw new Exception("path " + folderPath + " to store results is not a directory");
			}
		}
		checkImplementation(line);
		// closure strategy
		if (line.hasOption("c")) {
			try {
				closureType = ClosureType.valueOf(line.getOptionValue("c").toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (closureType == null)
				throw new Exception("not recognized closure method: " + line.getOptionValue("c"));
		} else
			closureType = ClosureType.BASIC;
		// thread mode
		if (line.hasOption("t")) {
			try {
				poolMode = PoolMode.valueOf(line.getOptionValue("t").toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (poolMode == null)
				throw new Exception("not recognized thread pool mode: " + line.getOptionValue("t"));
		} else
			poolMode = PoolMode.MONO;
		if (poolMode != PoolMode.MONO && closureType == ClosureType.WITH_HISTORY) {
			throw new Exception("closure with history is not compatible with thread pool mode: " + poolMode);
		}
		if (line.hasOption("h")) {
			String thresholdString = line.getOptionValue("h");
			try {
				threshold = Integer.parseInt(thresholdString);
				if (threshold < 1)
					throw new Exception();
			} catch (Exception e) {
				throw new Exception("invalid threshold value for multithreading: " + thresholdString);
			}

		}
		if (line.hasOption("a")) {
			try {
				algo = AlgoRuleBasis.valueOf(line.getOptionValue("a"));
			} catch (IllegalArgumentException e) {
			}
			if (algo == null)
				throw new Exception("unknown algorithm: " + line.getOptionValue("a"));
		} else
			algo = AlgoRuleBasis.LINCBOPRUNING;
		sortedPrint = line.hasOption("b");
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
		// output format
		ruleBasisFormat=checkRuleBasisFormat(line, outputFileName,"o");

		// separator
		checkSeparator(line);
		// verbose
		checkVerbose(line);
	}

	/**
	 * Select closure strategy.
	 *
	 * @return the closure strategy
	 */
	private ClosureStrategy selectClosureStrategy() {

		// choose Closure strategy
		switch (closureType) {
		case WITH_HISTORY:
			return new ClosureWithHistory(ctx);
		case BASIC:
		default: {
			switch (poolMode) {
			case FORKJOINPOOL:
				return new ClosureDirectWithForkJoinPool(ctx, threshold);
			case MONO:
			default:
				return new ClosureDirect(ctx);
			}
		}
		}
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
			printWriter.close();
		}
		StringBuilder sb = new StringBuilder();
		for (int support : map.keySet()) {
			sb.append(String.format("support %d: %d rules\n", support, map.get(support).size()));
		}
	}

	/**
	 * Prints sorted implications.
	 *
	 * @param printWriter  the print writer
	 * @param implications the implications
	 */
	private void printSortedImplications(PrintWriter printWriter, List<Implication> implications) {
		TreeMap<Integer, List<Implication>> map = new TreeMap<>();

		for (Implication implication : implications) {
			List<Implication> list = map.get(implication.getSupport().cardinality());
			if (list == null) {
				list = new ArrayList<>();
				map.put(implication.getSupport().cardinality(), list);
			}
			list.add(implication);
		}
		ArrayList<Implication> result = new ArrayList<>();
		for (int support : map.keySet()) {
			result.addAll(map.get(support));
		}
		// output results
		RuleExporter exporter = RuleExporters.fromFormat(ruleBasisFormat.name());
		exporter.export(printWriter, result, ctx);
		printWriter.flush();
		printWriter.close();
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
	private void printReport(List<Implication> implications, AlgoRuleBasis algo, ClosureStrategy closureStrategy,
			Chrono chrono) throws IOException {
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
		keys.add("closure_type");
		values.add("" + closureType);
		keys.add("set_impl");
		values.add(impl.toString());
		keys.add("threshold");
		values.add("" + closureStrategy.threshold());
		keys.add("time_exec");
		values.add("" + chrono.getResult());
		keys.add("nb_implications");
		values.add("" + implications.size());
		keys.add("withClarification");
		values.add("" + withClarification);

		TreeMap<Integer, List<Implication>> map = new TreeMap<>();
		for (Implication implication : implications) {
			List<Implication> list = map.get(implication.getSupport().cardinality());
			if (list == null) {
				list = new ArrayList<>();
				map.put(implication.getSupport().cardinality(), list);
			}
			list.add(implication);
		}
		keys.add("support_0");
		List<Implication> list = map.get(0);
		if (list == null)
			values.add("0");
		else
			values.add("" + map.get(0).size());
		keys.add("nb_diff_support");
		values.add("" + map.size());
		keys.add("max_support");
		values.add("" + (map.isEmpty() ? -1 : map.lastKey()));

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
		ClosureStrategy closureStrategy = selectClosureStrategy();
		AbstractAlgo<List<Implication>> linCbO;
		Chrono chrono = new Chrono("lincbo");
		switch (algo) {
		case LINCBO:
			linCbO = new LinCbO(ctx, chrono, closureStrategy, withClarification);
			break;
		case LINCBOPRUNING:
		default:
			linCbO = new LinCbOWithPruning(ctx, chrono, closureStrategy, withClarification);
		}
		System.out.println("running " + algo + " (" + impl + "/" + closureType + "/" + poolMode
				+ (withClarification ? "/CLARIFIED" : "") + ") data: " + inputFile.getName() + " ( "
				+ ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		chrono.start(linCbO.getDescription());
		linCbO.run();
		chrono.stop(linCbO.getDescription());
		List<Implication> result = linCbO.getResult();
		// display info
		if (verbose) {
			TreeMap<Integer, List<Implication>> map = new TreeMap<>();

			for (Implication implication : result) {
				List<Implication> list = map.get(implication.getSupport().cardinality());
				if (list == null) {
					list = new ArrayList<>();
					map.put(implication.getSupport().cardinality(), list);
				}
				list.add(implication);
			}
			String s = "";
			for (int support : map.keySet()) {
				s += String.format("support %d: %d rules\n", support, map.get(support).size());
			}
			s += "total: " + result.size() + "\n";
			System.out.println(s);
		}
		System.out.println("duration: " + chrono.getResult(linCbO.getDescription()) + " ms");
		// output results
		PrintWriter pw;
		if (outputFile != null)
			pw = new PrintWriter(outputFile);
		else
			pw = new PrintWriter(System.out);
		if (sortedPrint)
			printSortedImplications(pw, result);
		else {
			// output results
			RuleExporter exporter = RuleExporters.fromFormat(ruleBasisFormat.name());
			exporter.export(pw, result, ctx);
		}
		pw.flush();
		pw.close();

		if (reportFile != null)
			printReport(result, algo, closureStrategy, chrono);
		if (resultFolder != null) {
			printBySupport(resultFolder, result);
		}
		return result;
	}

	public static void main(String[] args) throws IOException {
//		test("example0.5.2");
//		test("FontaineDuTheil_attribute");
//		test("PlantSpecies");
//		test("exemple");
//		test("dbasis_10x22");
//		test("Plant");
//		test("ProtSystem");
//		test("distinct",true);
//		test("tags");
//		compareBasis("association","association");
//		compareBasis("association_r","association");
//		compareBasis("example16","example16");
//		compareBasis("example16_r","example16");
//		compareBasisWithReduced("example16","example16");
//		findCycles2("example16_r");
//		compareBasis("dbasis_10x21");
//		compareBasis("dbasis_10x22");
//		compareBasis("ord5bikesharing_day_cut_r");
//		compareBasis("ord5bikesharing_day_cut","ord5bikesharing_day_cut");
//		compareBasisWithReduced("ord5bikesharing_day_cut","ord5bikesharing_day_cut");
//		compareBasis("UsedPlant","UsedPlant");
//		findCycles("ord5bikesharing_day_cut_r");
//		findCycles("ord5bikesharing_day_cut");
//		compareBasis("association","association");
//		compareBasis("association_r");
//		findCycles("association_r");
//		compareBasis("association_c");
//		compareBasis("ord10shuttle","ord10shuttle");
//		compareBasis("ord10shuttle_r","ord10shuttle");
//		compareBasis("ord10shuttle_ro","ord10shuttle");
//		compareBasis("ord10shuttle_ra","ord10shuttle");
//		findCycles("ord10shuttle_r");
//		testOrderedDirect("ord10shuttle_r");
//		testOrderedDirect("ord10shuttle_r_kira");
//		testOrderedDirect("association_r");
//		testOrderedDirect("ord6magic04_r");

//		compareBasis("ord6magic04","ord6magic04");
//		compareBasis("ord6magic04_r","ord6magic04");
//		findCycles("ord6magic04_r");
//		compareBasis("role");
//		compareBasis("attribute");
//		test("UsedPlant");
//		test("dbasis_10x21");
//		test("dbasis_9x8");
//		compareBasis("ProtectedOrganism","ProtectedOrganism");
		test18vs23();
	}

	final static int DIRECT_TEST_DEEP = 3;

	private static void findCycles2(String name1) throws IOException {
		IBinaryContext context = SLFReader.read(new File("c:/projects/rules/ebasis/" + name1 + ".slf"));
		RuleBasis myBasis = RuleBasisReader.read("c:/projects/rules/ebasis/" + name1 + ".txt", context);
		ClosureStrategy closureEngine=new ClosureDirect(context);	
		// compute duquenne guigues
		LinCbO linCbO = new LinCbO(context, null, closureEngine, false);
		linCbO.run();
		RuleBasis dqBasis = new RuleBasis(linCbO.getResult(), context);
		System.out.println("DQbasis D-cycles=" + RuleUtilities.findDCycles(dqBasis.getImplications()));
		System.out.println("Mybasis D-cycles=" + RuleUtilities.findDCycles(myBasis.getImplications()));		
	}
		private static void findCycles(String name1) throws IOException {
		IBinaryContext context = SLFReader.read(new File("c:/projects/rules/expe2/" + name1 + ".slf"));
		RuleBasis myBasis = RuleBasisReader.read("c:/projects/rules/expe2/" + name1 + ".txt", context);
		RuleBasis kiraBasis = RuleBasisReader.read("c:/projects/rules/expe2/" + name1 + "_kira.txt", context);
		ClosureStrategy closureEngine=new ClosureDirect(context);	
		// compute duquenne guigues
		LinCbO linCbO = new LinCbO(context, null, closureEngine, false);
		linCbO.run();
		RuleBasis dqBasis = new RuleBasis(linCbO.getResult(), context);
		System.out.println("DQbasis D-cycles=" + RuleUtilities.findDCycles(dqBasis.getImplications()));
		System.out.println("Mybasis D-cycles=" + RuleUtilities.findDCycles(myBasis.getImplications()));
		System.out.println("Kirabasis D-cycles=" + RuleUtilities.findDCycles(kiraBasis.getImplications()));
	}

		private static void compareBasisWithReduced(String name1,String rep) throws IOException {
		IBinaryContext context = SLFReader.read(new File("c:/projects/rules/"+rep+"/" + name1 + ".slf"));
			RuleBasis myBasis = RuleBasisReader.read("c:/projects/rules/"+rep+"/" + name1 + ".txt", context);
			RuleBasis reducedBasis = RuleBasisReader.read("c:/projects/rules/"+rep+"/" +name1+ "_r.txt", context);
			System.out.println("reducedBasis<myBasis="
					+ RuleUtilities.isIncludedIn(reducedBasis.getImplications(), myBasis.getImplications()));
			System.out.println("myBasis<reducedBasis="
					+ RuleUtilities.isIncludedIn(myBasis.getImplications(), reducedBasis.getImplications()));
			
		}
		private static void compareBasis(String name1,String rep) throws IOException {
		IBinaryContext context = SLFReader.read(new File("c:/projects/rules/"+rep+"/" + name1 + ".slf"));
		RuleBasis myBasis = RuleBasisReader.read("c:/projects/rules/"+rep+"/" + name1 + ".txt", context);
		RuleBasis kiraBasis = RuleBasisReader.read("c:/projects/rules/"+rep+"/" +name1+ "_kira.txt", context);
		ClosureStrategy closureEngine = new ClosureDirect(context);
		// compute duquenne guigues
		LinCbO linCbO = new LinCbO(context, null, closureEngine, false);
		linCbO.run();
		RuleBasis dqBasis = new RuleBasis(linCbO.getResult(), context);
		System.out.println("DQbasis<kiraBasis="
				+ RuleUtilities.isIncludedIn(dqBasis.getImplications(), kiraBasis.getImplications()));
		System.out.println("kiraBasis<DQbasis="
				+ RuleUtilities.isIncludedIn(kiraBasis.getImplications(), dqBasis.getImplications()));
		System.out.println("DQbasis<myBasis="
				+ RuleUtilities.isIncludedIn(dqBasis.getImplications(), myBasis.getImplications()));
		System.out.println("myBasis<DQbasis="
				+ RuleUtilities.isIncludedIn(myBasis.getImplications(), dqBasis.getImplications()));
		System.out.println("myBasis<kiraBasis="
				+ RuleUtilities.isIncludedIn(myBasis.getImplications(), kiraBasis.getImplications()));
		System.out.println("kiraBasis<myBasis="
				+ RuleUtilities.isIncludedIn(kiraBasis.getImplications(), myBasis.getImplications()));

	}

		private static void test18vs23() throws IOException {
		IBinaryContext initial_context = SLFReader.read(new File("c:\\projects\\rules\\inter1shuttle\\inter4shuttle.slf"));
		System.out.println("context=" + initial_context.getObjectCount() + "x" + initial_context.getAttributeCount());
		// compute duquenne guigues
		ClosureStrategy closureEngine = new ClosureDirect(initial_context);
		LinCbO linCbO = new LinCbO(initial_context, null, closureEngine, false);
		linCbO.run();
		List<Implication> dqBasis = linCbO.getResult();
		DBaseV23 calculator23 = new DBaseV23(initial_context, 1, -1);
		calculator23.run();
		RuleBasis ruleBaseDB23 = new RuleBasis(calculator23.getResult(), initial_context);
		DBaseV18 calculator18 = new DBaseV18(initial_context, 1 , -1);
		calculator18.run();
		RuleBasis ruleBaseDB18 = new RuleBasis(calculator18.getResult(), initial_context);
		
		compareNonBinaryBasis(ruleBaseDB18.getImplications(), ruleBaseDB23.getImplications());
		System.out.println(
				"ruleBaseDQ<ruleBaseDB23=" + RuleUtilities.isIncludedIn(dqBasis, ruleBaseDB23.getImplications()));
		System.out.println(
				"ruleBaseDB23<ruleBaseDQ=" + RuleUtilities.isIncludedIn(ruleBaseDB23.getImplications(), dqBasis));
		System.out.println("DB18 is direct= " + RuleUtilities.isDirect(ruleBaseDB18.getImplications(), DIRECT_TEST_DEEP,
				initial_context.getFactory()));
		System.out.println("DB23 is direct= " + RuleUtilities.isDirect(ruleBaseDB23.getImplications(), DIRECT_TEST_DEEP,
				initial_context.getFactory()));
		System.out.println(
				"ruleBaseDB18<ruleBase23=" + RuleUtilities.isIncludedIn(ruleBaseDB18.getImplications(), ruleBaseDB23.getImplications()));
		System.out.println(
				"ruleBaseDB23<ruleBase18=" + RuleUtilities.isIncludedIn(ruleBaseDB23.getImplications(), ruleBaseDB18.getImplications()));
		System.out.println("ruleBaseDB23 card="+ruleBaseDB23.getImplications().size());
		System.out.println("ruleBaseDB18 card="+ruleBaseDB18.getImplications().size());
		
/*		System.out.println("*****V18************************");
		for(Implication impl:ruleBaseDB18.getImplications())
		{
				System.out.println(impl);
		}
		System.out.println("*****V23************************");
		for(Implication impl:calculator23.getResult())
		{
				System.out.println(impl);
		}
*/			
		}
		private static void compareNonBinaryBasis(List<Implication> list1,List<Implication> list2) {
			HashSet<Implication> basis1=new HashSet<>();
			HashSet<Implication> basis2=new HashSet<>();
			for(Implication impl:list1) basis1.add(impl);
			for(Implication impl:list2) basis2.add(impl);
			for(Implication impl:basis1)
			{
				if(!basis2.contains(impl))
					System.out.println("only in basis1:"+impl);
			}
			for(Implication impl:basis2)
			{
				if(!basis1.contains(impl))
					System.out.println("only in basis2:"+impl);
			}
			
		}
		private static void test(String name) throws IOException {
		IBinaryContext initial_context = SLFReader.read(new File("c:/projects/rules/expe/" + name + ".slf"));
		System.out.println("context=" + initial_context.getObjectCount() + "x" + initial_context.getAttributeCount());

		ClosureStrategy closureEngine = new ClosureDirect(initial_context);
		// compute duquenne guigues
		LinCbO linCbO = new LinCbO(initial_context, null, closureEngine, false);
		linCbO.run();
		List<Implication> dqBasis = linCbO.getResult();
		DBaseV20 calculator = new DBaseV20(initial_context, 0, -1);
		calculator.run();
		// print result
		PrintWriter pw = new PrintWriter("c:/projects/rules/dbasis/" + name + "DB2.txt");
		RuleExporter exporter = RuleExporters.fromFormat(RuleBasisFormat.TXT.name());
		exporter.export(pw, calculator.getResult(), initial_context);
		pw.flush();
		pw.close();

		RuleBasis ruleBaseDB2 = new RuleBasis(calculator.getResult(), initial_context);
		System.out.println(
				"ruleBaseDQ<ruleBaseDB2=" + RuleUtilities.isIncludedIn(dqBasis, ruleBaseDB2.getImplications()));
		System.out.println(
				"ruleBaseDB2<ruleBaseDQ=" + RuleUtilities.isIncludedIn(ruleBaseDB2.getImplications(), dqBasis));
		System.out.println("DB2 is direct= " + RuleUtilities.isDirect(ruleBaseDB2.getImplications(), DIRECT_TEST_DEEP,
				initial_context.getFactory()));
		testOrderedDirect(name);
	}

	private static void testOrderedDirect(String name) throws IOException {
		IBinaryContext context = SLFReader
				.read(new File("c:/projects/rules/expe2/" + name + ".slf")/* ,new RoaringBitMapFactory() */);
		RuleBasis ruleBase = RuleBasisReader.read("c:/projects/rules/expe2/" + name + ".txt", context);

		List<Implication> nonBinaryBasis = new ArrayList<>();
		List<Implication> binaryBasis = new ArrayList<>();
		for (Implication impl : ruleBase.getImplications()) {
			if (impl.getPremise().cardinality() <= 1)
				binaryBasis.add(impl);
			else
				nonBinaryBasis.add(impl);
		}
		List<Implication> dBasis = new ArrayList<>(binaryBasis);
		dBasis.addAll(nonBinaryBasis);
		Chrono chrono = new Chrono("ordered direct test");
		chrono.start("dBasis");
		System.out.println(
				"DBasis initial is direct= " + RuleUtilities.isDirect(dBasis, DIRECT_TEST_DEEP, context.getFactory()));
		chrono.stop("dBasis");
		dBasis.removeAll(nonBinaryBasis);
		Collections.shuffle(nonBinaryBasis);
		dBasis.addAll(nonBinaryBasis);
		chrono.start("shuffle");
		System.out.println("DBasis with shuffle is direct= "
				+ RuleUtilities.isDirect(dBasis, DIRECT_TEST_DEEP, context.getFactory()));
		chrono.stop("shuffle");

		dBasis.removeAll(nonBinaryBasis);
		Collections.sort(nonBinaryBasis, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a2.getPremise().cardinality(), a1.getPremise().cardinality());
			}
		});
		dBasis.addAll(nonBinaryBasis);
		chrono.start("inverse");
		System.out.println("DBasis with cardinality inverse is direct= "
				+ RuleUtilities.isDirect(dBasis, DIRECT_TEST_DEEP, context.getFactory()));
		chrono.stop("inverse");
		for (String serie : chrono.getSerieNames()) {
			System.out.println(serie + " time: " + chrono.getResult(serie));
		}
		System.out.println("Total time: " + chrono.getResult());
	}

	public static double calculateAverageSize(List<Implication> items) {
		if (items == null || items.isEmpty())
			return 0.0;

		double total = 0.0;
		for (Implication impl : items) {
			total += impl.getPremise().cardinality();
		}

		return total / items.size();
	}

	public static double calculateMedianSize(List<Implication> items) {
		if (items == null || items.isEmpty())
			return 0.0;

		List<Integer> sizes = new ArrayList<>();
		for (Implication impl : items) {
			sizes.add(impl.getPremise().cardinality());
		}

		Collections.sort(sizes);

		int middle = sizes.size() / 2;
		if (sizes.size() % 2 == 0) {
			// moyenne des deux valeurs du milieu
			return (sizes.get(middle - 1) + sizes.get(middle)) / 2.0;
		} else {
			return sizes.get(middle);
		}
	}

}
