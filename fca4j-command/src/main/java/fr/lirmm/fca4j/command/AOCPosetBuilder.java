/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.algo.AOC_poset_Ares;
import fr.lirmm.fca4j.algo.AOC_poset_Ceres;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.cli.io.ConceptOrderJSONWriter;
import fr.lirmm.fca4j.cli.io.ConceptOrderXMLWriter;
import fr.lirmm.fca4j.cli.io.SLFReader;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.natif.FastAOCPosetHermes;
import fr.lirmm.fca4j.core.natif.FastAOCPosetPluton;
import fr.lirmm.fca4j.core.natif.impl.NativeAOCPosetHermes;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.GraphVizDotWriter;

/**
 * The Class AOCPosetBuilder.
 */
public class AOCPosetBuilder extends ConceptOrderBuilder {
	
	/** The output file. */
	protected File outputFile;
	
	/** The input file. */
	protected File inputFile;
	
	/** The binary context. */
	protected IBinaryContext ctx;
	
	/** The input format. */
	protected ContextFormat inputFormat;
	
	/** The algo. */
	protected AlgoAOCPoset algo;

	/** use native code when available (HERMES, PLUTON), false by default */
	protected boolean useNativeCode = false;

	/**
	 * The Enum AlgoAOCPoset.
	 */
	enum AlgoAOCPoset {
		
		/** The ares algorithm. */
		ARES, 
		 /** The ceres algorithm. */
		 CERES, 
		 /** The pluton algorithm. */
		 PLUTON, 
		 /** The hermes algorithm. */
		 HERMES 
		 /*,ATHENA*/
	};

	/**
	 * Instantiates a new AOC poset builder.
	 *
	 * @param setContext the set context
	 */
	public AOCPosetBuilder(ISetContext setContext) {
		super("aocposet",
				"build a sub-order of the concept lattice restricted to attribute-concepts and object-concepts",setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		StringBuilder sb_algo_aoc = new StringBuilder();
		for (AlgoAOCPoset algo : AlgoAOCPoset.values()) {
			sb_algo_aoc.append("\n* " + algo.name());
			if(algo.name().equalsIgnoreCase("athena")) 
				sb_algo_aoc.append(" (experimental)");
		}
		sb_algo_aoc.append(" (default)");
		// algo
		options.addOption(
				Option.builder("a").desc("supported algorithms are:" + sb_algo_aoc).hasArg().argName("ALGO").build());
		// input format
		declareContextFormat("i","INPUT-FORMAT");
		// output format
		declareOutputFormat();
		// graphviz
		declareGraphvizOptions();
		// implications
//		declareImplicationsOptions();
		// implementation
		declareImplementation(false);
		// native code (HERMES only)
		options.addOption(Option.builder("native")
				.desc("enable native code (CRoaring/JNI) for HERMES, use C implementation instead")
				.build());
		// concept descriptors
		declareConceptDescriptorOptions();
		// common options
		declareCommon();
	}

	/**
	 * Check options.
	 *
	 * @param line the line
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
		checkOutputFormat(line, outputFileName);
		checkDotFile(line);
		checkIDFFile(line);
		checkIBFFile(line);
		checkITPFile(line);
		checkImplementation(line);
		if (line.hasOption("a")) {
			try {
				algo = AlgoAOCPoset.valueOf(line.getOptionValue("a").toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (algo == null)
				throw new Exception("unknown algorithm: " + line.getOptionValue("a"));
		} else
			algo = AlgoAOCPoset.HERMES;
		// concept descriptor options
		checkConceptDescriptorOptions(line);
		// native code
		useNativeCode = line.hasOption("native");
		// separator
		checkSeparator(line);
		// verbose
		checkVerbose(line);
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
		Chrono chrono = new Chrono("aocposet");
		AbstractAlgo<IConceptOrder> aoc_algo;
		switch (algo) {
//		case ATHENA:
//			aoc_algo = new AOC_poset_Athena(ctx, chrono);
//			break;
		case HERMES:
			if (useNativeCode) {
				aoc_algo = FastAOCPosetHermes.create(ctx);
			} else {
				aoc_algo = new AOC_poset_Hermes(ctx, chrono);
			}
			break;
		case PLUTON:
			if (useNativeCode) {
				aoc_algo = FastAOCPosetPluton.create(ctx);
			} else {
				aoc_algo = new AOC_poset_Pluton(ctx, chrono);
			}			
			break;
		case ARES:
			aoc_algo = new AOC_poset_Ares(ctx, chrono, null, true, true);
			break;
		case CERES:
			aoc_algo = new AOC_poset_Ceres(ctx, chrono);
			break;
		default:
			throw new Exception("unknown algorithm");
		}
		String engine = (aoc_algo instanceof NativeAOCPosetHermes) ? "native C" : "java";
		System.out.println("running " + algo + " (" + impl + ", " + engine + ") data: " + inputFile.getName() + " ( " + ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		chrono.start(aoc_algo.getDescription());
		aoc_algo.run();
		chrono.stop(aoc_algo.getDescription());
		IConceptOrder result = aoc_algo.getResult();

		// output result
		BufferedWriter writer;
		String outputName;
		if (outputFile != null) {
			writer = new BufferedWriter(new FileWriter(outputFile));
			outputName = outputFile.getName();
		} else {
			writer = new BufferedWriter(new OutputStreamWriter(System.out));
			outputName = "standard output stream";
		}
		if (outputFile != null)
			switch (outputFormat) {
			case XML:
				ConceptOrderXMLWriter.write(writer, result, ctx);
				break;
			case JSON:
			    ConceptOrderJSONWriter.writeStreamingFast(writer, result, ctx.getName(), aoc_algo.getDescription());
			    writer.close();
			    break;			    
			}
		// graphviz
		if (dotFile != null) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
			boolean displaySize = true;
//			boolean alignSibling = true;
			String senseLayout = "BT";
			GraphVizDotWriter dotWriter = new GraphVizDotWriter(displayMode, displaySize,false,senseLayout,computeStability);
			dotWriter.write(bw, result );
		}
		// implications (topological sort)
		if (itpFile != null) {
			PrintWriter pw = new PrintWriter(new FileWriter(itpFile));
			printImplications(pw, result.getTopologicalImplications(),result.getContext());
			pw.flush();
			pw.close();
		}
		// implications (depth first)
		if (idfFile != null) {
			PrintWriter pw = new PrintWriter(new FileWriter(idfFile));
			printImplications(pw, result.getDepthFirstImplications(),result.getContext());
			pw.flush();
			pw.close();
		}
		// implications (breadth first)
		if (ibfFile != null) {
			PrintWriter pw = new PrintWriter(new FileWriter(ibfFile));
			printImplications(pw, result.getBreadthFirstImplications(),result.getContext());
			pw.flush();
			pw.close();
		}
		if(cdFolder!=null)
			produceConceptDescriptorsInFolder(result);
		if (cdFile != null)
			produceConceptDescriptorsInFile(result);
		// display chrono
		for(String serie:chrono.getSerieNames()) {
			System.out.println(serie+": "+chrono.getResult(serie));
		}
		if (verbose) {
			System.out.println("done");
			System.out.println("concepts: "+result.getConceptCount() + " edges: "+result.getEdgeCount());

		}
		return result;
	}
	public static void main(String[] args) throws IOException {
		inspect("association");
		inspect("attribute");
		inspect("class");
		inspect("operation");
		inspect("role");
		
	}
	protected static void inspect(String name)  throws IOException{
		IBinaryContext context_class=SLFReader.read(new File("c:/projects/FontaineDuTheil/"+name+".slf"));
		
	}
}
