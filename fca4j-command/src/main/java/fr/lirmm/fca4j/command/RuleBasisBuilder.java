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
import java.util.Iterator;
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
import fr.lirmm.fca4j.algo.DBaseCalculator;
import fr.lirmm.fca4j.algo.DBaseCalculator2;
import fr.lirmm.fca4j.algo.LinCbO;
import fr.lirmm.fca4j.algo.LinCbOWithPruning;
import fr.lirmm.fca4j.cli.io.RuleBasisReader;
import fr.lirmm.fca4j.cli.io.SLFReader;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class RuleBasisBuilder.
 */
public class RuleBasisBuilder extends Command {
	
	/** The Constant DEFAULT_THRESHOLD. */
	public final static int DEFAULT_THRESHOLD=50;
	
	/** The output file. */
	protected File outputFile;
	
	/** The input file. */
	protected File inputFile;
	
	/** The report file. */
	protected File reportFile;
	
	/** if report exist. */
	protected boolean reportExist=false;
	
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
	
	/** The threshold. */
	protected int threshold=DEFAULT_THRESHOLD;
	
	/** with clarification. */
	protected boolean withClarification=false;
	
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
	 //, EXECUTOR_SERVICE
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
		super("rulebasis", "compute the canonical basis of implications (Duquenne-Guigues)",setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		options.addOption(
				Option.builder("clarify").desc("this option can speed up the execution by basing the calculation on the clarified context").build());
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
				.desc("multithreading options are:\n* MONO (default)\n* FORKJOINPOOL").hasArg()
				.argName("POOL-MODE").build());
		options.addOption(Option.builder("h")
				.desc("multithreading limit size of work for a task default=50 ").hasArg()
				.argName("THRESHOLD").build());
		options.addOption(Option.builder("b").desc("sort implications by ascending support").build());
		// output report
		options.addOption(
				Option.builder("r").desc("generate report about algorithm execution").hasArg().argName("REPORTFILE").build());
		// folder output with 1 file by support
		options.addOption(
				Option.builder("folder").desc("folder to generate a file by support for results").hasArg().argName("PATH").build());
		// input format
		declareContextFormat("i","INPUT-FORMAT");
		// output format
		options.addOption(Option.builder("o").desc("supported formats are:\n* TXT (default)\n* JSON\n* XML\n* DATALOG")
				.hasArg().argName("OUTPUT-FORMAT").build());
		
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
		withClarification=line.hasOption("clarify");
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
		if(line.hasOption("folder")){
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
		if(poolMode!=PoolMode.MONO && closureType==ClosureType.WITH_HISTORY)
		{
			throw new Exception("closure with history is not compatible with thread pool mode: " + poolMode);
		}
		if(line.hasOption("h")){
			String thresholdString = line.getOptionValue("h");
			try {
				threshold = Integer.parseInt(thresholdString);
				if (threshold < 1)
					throw new Exception();
			} catch (Exception e) {
				throw new Exception(
						"invalid threshold value for multithreading: "+thresholdString);
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
			else reportExist=true;
		} else
			reportFile = null;
		
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
				return new ClosureDirectWithForkJoinPool(ctx,threshold);
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
     * @param folder the folder
     * @param implications the implications
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void printBySupport(File folder,List<Implication> implications) throws IOException {
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
                FileWriter fileWriter = new FileWriter(folder.getPath() + File.separator + ctx.getName() + support + "Rules.txt");
                PrintWriter printWriter = new PrintWriter(fileWriter);
                printImplications(printWriter, map.get(support));
                printWriter.close();
            }
        StringBuilder sb = new StringBuilder();
        for (int support : map.keySet()) {
            sb.append(String.format("support %d: %d rules\n", support, map.get(support).size()));
        }
    }
	
	/**
	 * Prints the implications.
	 *
	 * @param printWriter the print writer
	 * @param implications the implications
	 */
	private void printImplications(PrintWriter printWriter, List<Implication> implications) {
		for (Implication implication : implications) {
			printWriter.printf("<%d> %s => %s\n", implication.getSupport().cardinality(), displayAttrs(implication.getPremise()),
					displayAttrs(implication.getConclusion()));
		}

	}

	/**
	 * Prints sorted implications.
	 *
	 * @param printWriter the print writer
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
		for (int support : map.keySet()) {
			printImplications(printWriter, map.get(support));

		}
	}

	/**
	 * Display attributes.
	 *
	 * @param set the set
	 * @return the string
	 */
	private String displayAttrs(ISet set) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			if (sb.length() != 0) {
				sb.append(",");
			}
			sb.append(ctx.getAttributeName(it.next()));
		}
		return sb.toString();
	}
	
	/**
	 * Prints the report.
	 *
	 * @param implications the implications
	 * @param algo the algorithm
	 * @param closureStrategy the closure strategy
	 * @param chrono the chrono
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void printReport(List<Implication> implications,AlgoRuleBasis algo,ClosureStrategy closureStrategy,Chrono chrono) throws IOException{
		CSVWriter writer=new CSVWriter(new FileWriter(reportFile,reportExist),separator);
		ArrayList<String> keys=new ArrayList<>();
		ArrayList<String> values=new ArrayList<>();
		keys.add("source");
		values.add(inputFile.getName());
		keys.add("nb_objects");
		values.add(""+ctx.getObjectCount());
		keys.add("nb_attributes");
		values.add(""+ctx.getAttributeCount());
		keys.add("incidence");
		values.add(""+ctx.getIncidence());		
		keys.add("timestamp");
		values.add( ""+new Timestamp(System.currentTimeMillis()).getTime());
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
		values.add(""+Runtime.getRuntime().availableProcessors());		
		keys.add("algo");
		values.add(algo.toString());
		keys.add("pool_mode");
		values.add(""+poolMode);
		keys.add("closure_type");		
		values.add(""+closureType);
		keys.add("set_impl");
		values.add(impl.toString());
		keys.add("threshold");
		values.add(""+closureStrategy.threshold());
		keys.add("time_exec");
		values.add(""+chrono.getResult());
		keys.add("nb_implications");
		values.add(""+implications.size());
		keys.add("withClarification");
		values.add(""+withClarification);
		
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
		List<Implication> list=map.get(0);
		if(list==null)
			values.add("0");
		else values.add(""+map.get(0).size());
		keys.add("nb_diff_support");
		values.add(""+map.size());
		keys.add("max_support");
		values.add(""+(map.isEmpty()?-1:map.lastKey()));
		
		ArrayList<String[]> records=new ArrayList<>();
		records.add(keys.toArray(new String[keys.size()]));
		records.add(values.toArray(new String[values.size()]));
		if(!reportExist)
			writer.writeAll(records);
		else writer.writeNext(records.get(1));
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
			linCbO = new LinCbO(ctx, chrono, closureStrategy,withClarification);
			break;
		case LINCBOPRUNING:
		default:
			linCbO = new LinCbOWithPruning(ctx, chrono, closureStrategy,withClarification);
		}
		System.out.println("running " + algo + " (" + impl + "/" + closureType+"/"+poolMode
				+(withClarification?"/CLARIFIED":"")+ ") data: " + inputFile.getName() + " ( " + ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
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
		else
			printImplications(pw, result);		
		pw.flush();
		pw.close();
		
		if(reportFile!=null)
			printReport(result, algo, closureStrategy, chrono);
		if(resultFolder!=null){
			printBySupport(resultFolder, result);
		}
		return result;
	}
	public static void main(String[] args) throws IOException {
		test2("example0.5.2");
//		test2("PlantSpecies");
	}
	private static void test2(String name) throws IOException  {
		IBinaryContext context=SLFReader.read(new File("c:/projects/rules/dbasis/"+name+".slf"));
		System.out.println("***************************");
		System.out.println("context="+context.getObjectCount()+"x"+context.getAttributeCount());
		RuleBasis ruleBaseDB=RuleBasisReader.read("c:/projects/rules/dbasis/"+name+"DB.txt",context);
		RuleBasis ruleBaseDQ=RuleBasisReader.read("c:/projects/rules/dbasis/"+name+"DQ.txt",context);
		
		DBaseCalculator2 calculator=new DBaseCalculator2(context);
		calculator.run();
		printImplications2(calculator.getResult(),context);
		RuleBasis ruleBaseDB2=new RuleBasis(calculator.getResult(),context);
		
		System.out.println("ruleBaseDQ<ruleBaseDB="+ruleBaseDQ.isIncludedIn(ruleBaseDB));
		System.out.println("ruleBaseDB<ruleBaseDQ="+ruleBaseDB.isIncludedIn(ruleBaseDQ));
		System.out.println("ruleBaseDB<ruleBaseDB2="+ruleBaseDB.isIncludedIn(ruleBaseDB2));
		System.out.println("ruleBaseDB2<ruleBaseDB="+ruleBaseDB2.isIncludedIn(ruleBaseDB));
		System.out.println("ruleBaseDB2<ruleBaseDQ="+ruleBaseDB2.isIncludedIn(ruleBaseDQ));
		System.out.println("ruleBaseDB<ruleBaseDB2="+ruleBaseDB.isIncludedIn(ruleBaseDB2));
		
	}

	private static void test(String name) throws IOException  {
		IBinaryContext context=SLFReader.read(new File("c:/projects/rules/PlantSpecies/"+name+".slf"));
		System.out.println("***************************");
		System.out.println("context="+context.getObjectCount()+"x"+context.getAttributeCount());
		RuleBasis ruleBaseDQ=RuleBasisReader.read("c:/projects/rules/PlantSpecies/"+name+"DQ.txt",context);
		RuleBasis ruleBaseDB=RuleBasisReader.read("c:/projects/rules/PlantSpecies/"+name+"DB.txt",context);
		
		DBaseCalculator calculator=new DBaseCalculator(context);
		calculator.run();
		printImplications2(calculator.getResult(),context);
		RuleBasis ruleBaseDB2=new RuleBasis(calculator.getResult(),context);
		
		System.out.println("ruleBaseDQ<ruleBaseDB="+ruleBaseDQ.isIncludedIn(ruleBaseDB));
		System.out.println("ruleBaseDB<ruleBaseDQ="+ruleBaseDB.isIncludedIn(ruleBaseDQ));
		System.out.println("ruleBaseDB<ruleBaseDB2="+ruleBaseDB.isIncludedIn(ruleBaseDB2));
		System.out.println("ruleBaseDB2<ruleBaseDB="+ruleBaseDB2.isIncludedIn(ruleBaseDB));
		System.out.println("ruleBaseDB2<ruleBaseDQ="+ruleBaseDB2.isIncludedIn(ruleBaseDQ));
		System.out.println("ruleBaseDB<ruleBaseDB2="+ruleBaseDB.isIncludedIn(ruleBaseDB2));
		
	}
	private static void printImplications2(List<Implication> implications,IBinaryContext context) {
		for (Implication implication : implications) {
			System.out.printf("<%d> %s => %s\n", implication.getSupport().cardinality(), displayAttrs2(implication.getPremise(),context),
					displayAttrs2(implication.getConclusion(),context));
		}

	}
	private static String displayAttrs2(ISet set,IBinaryContext context) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			if (sb.length() != 0) {
				sb.append(",");
			}
			sb.append(context.getAttributeName(it.next()));
		}
		return sb.toString();
	}
	
}
