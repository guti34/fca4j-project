/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.algo.Lattice_AddIntent;
import fr.lirmm.fca4j.algo.Lattice_Iceberg;
import fr.lirmm.fca4j.algo.Lattice_ParallelCbO;
import fr.lirmm.fca4j.cli.io.ConceptOrderJSONWriter;
import fr.lirmm.fca4j.cli.io.ConceptOrderXMLWriter;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.natif.FastLatticeAddExtent;
import fr.lirmm.fca4j.core.natif.FastLatticeCbO;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.GraphVizDotWriter;

/**
 * The Class LatticeBuilder.
 */
public class LatticeBuilder extends ConceptOrderBuilder {
	
	/** The output file. */
	protected File outputFile;
	
	/** The input file. */
	protected File inputFile;
	
	/** The context. */
	protected IBinaryContext ctx;
	
	/** The input format. */
	protected ContextFormat inputFormat;
	
	/** The algorithm. */
	protected AlgoLattice algo;
	
	/** The percent. */
	protected int percent = -1;

	/** use native code when available (ADD_EXTENT or PARALLEL_CBO), false by default */
	protected boolean useNativeCode = false;

	/**
	 * The Enum AlgoLattice.
	 */
	enum AlgoLattice {
		
		/**The lattice parallel Cbo algo */
		PARALLEL_CBO,
		/** The add extent algo. */
		ADD_EXTENT, 
		/** The LYAB algo */
		ADD_INTENT,
		 /** The iceberg algo. */
		 ICEBERG
	};

	/**
	 * Instantiates a new lattice builder.
	 *
	 * @param setContext the set context
	 */
	public LatticeBuilder(ISetContext setContext) {
		super("lattice",
				"builds a concept lattice. PARALLEL_CBO, ADD_EXTENT and ADD_INTENT algorithms build the complete concept lattice. ICEBERG builds a lattice with only the top-most concepts of the concept lattice. A bottom concept is added that groups remaining attributes (not introduced by a frequent concept extent) and transforms the semi-lattice into a lattice. Euclidian division is used in FCA4J: e.g. In Iceberg50,  the concepts have an extent whose cardinality is >= total object count * 50 /100, where / is Euclidian division",setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		StringBuilder sb_algo_lat = new StringBuilder();
		boolean first = true;
		for (AlgoLattice algo : AlgoLattice.values()) {
			sb_algo_lat.append("\n* " + algo.name());
			if (first) {
				sb_algo_lat.append(" (default)");
				first = false;
			}
		}
		// algo
		options.addOption(
				Option.builder("a").desc("Supported algorithms are:" + sb_algo_lat).hasArg().argName("ALGO").build());
		// percent
		options.addOption(Option.builder("p").desc("for ICEBERG: percentage (of extent) to keep the top-most concepts")
				.hasArg().argName("PERCENT").build());
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
		// native code (ADD_EXTENT or PARALLEL_CBO)
		options.addOption(Option.builder("native")
				.desc("enable native code (CRoaring/JNI), use C implementation instead")
				.build());
		// concept descriptors
		declareConceptDescriptorOptions();
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
		checkOutputFormat(line, outputFileName);
		checkDotFile(line);
		checkIDFFile(line);
		checkIBFFile(line);
		checkITPFile(line);
		checkImplementation(line);
		if (line.hasOption("a")) {
			try {
				algo = AlgoLattice.valueOf(line.getOptionValue("a").toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (algo == null)
				throw new Exception("unknown algorithm: " + line.getOptionValue("a"));
		} else
			algo = AlgoLattice.PARALLEL_CBO;
		// percent
		if (algo == AlgoLattice.ICEBERG) {
			if (!line.hasOption("p"))
				throw new Exception("a percentage must be specified as a parameter for ICEBERG algorithm (-p option)");
			String percentString = line.getOptionValue("p");
			if (percentString.endsWith("%"))
				percentString = percentString.substring(0, percentString.length() - 1);
			try {
				percent = Integer.parseInt(percentString);
				if (percent < 0 || percent > 100)
					throw new Exception();
			} catch (Exception e) {
				throw new Exception(
						"invalid parameter for ICEBERG  (-p option) specify a positive integer between [0-100]%");
			}
		}
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

			// Les consommateurs de sets COMPLETS : DOT, implications, descripteurs
			// datalog, et (par prudence) la sortie XML. La sortie JSON via
			// writeStreamingFast n'utilise que les sets réduits.
			boolean needFullSets = dotFile != null || itpFile != null || idfFile != null
					|| ibfFile != null || cdFolder != null || cdFile != null;

			Chrono chrono = new Chrono("lattice");		
		AbstractAlgo<IConceptOrder> lat_algo;
		// choose algo dynamically
		switch (algo) {
		case ADD_EXTENT:
			if (useNativeCode) {
				lat_algo = FastLatticeAddExtent.create(ctx,needFullSets);
			} else {
				lat_algo = new Lattice_AddExtent(ctx, chrono);
			}
			break;
		case ADD_INTENT:
			lat_algo = new Lattice_AddIntent(ctx,chrono);
			break;
		case PARALLEL_CBO:
			if (useNativeCode) {
			lat_algo = FastLatticeCbO.create(ctx,needFullSets);
			} else {
				lat_algo = new Lattice_ParallelCbO(ctx,chrono);
			}
			break;
		case ICEBERG:
			lat_algo = new Lattice_Iceberg(ctx, percent, chrono);
			break;

		default:
			throw new Exception("unknown algorithm");
		}
		String engine = useNativeCode ? "native C" : "java";
		System.out.println("running " + algo + " (" + impl + ", " + engine + ") data: "
				+ inputFile.getName() + " ( " + ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		chrono.start(lat_algo.getDescription());
		lat_algo.run();
		chrono.stop(lat_algo.getDescription());
		IConceptOrder result = lat_algo.getResult();
		chrono.start("write output");
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
			    ConceptOrderJSONWriter.writeStreamingFast(writer, result, ctx.getName(), lat_algo.getDescription());
			    writer.close();
			    break;
			}
		chrono.stop("write output");
		
		System.out.println("write output="+chrono.getResult("write output"));
		// graphviz
		if (dotFile != null) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
			boolean displaySize = true;
			boolean alignSibling = true;
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
			PrintWriter bw = new PrintWriter(new FileWriter(idfFile));
			printImplications(bw, result.getDepthFirstImplications(),result.getContext());
			bw.flush();
			bw.close();
		}
		// implications (breadth first)
		if (ibfFile != null) {
			PrintWriter bw = new PrintWriter(new FileWriter(ibfFile));
			printImplications(bw, result.getBreadthFirstImplications(),result.getContext());
			bw.flush();
			bw.close();
		}
		if(cdFolder!=null)
			produceConceptDescriptorsInFolder(result);
		if (cdFile != null)
			produceConceptDescriptorsInFile(result);
		// display chrono

		System.out.println("duration: " + chrono.getResult(lat_algo.getDescription()) + " ms");
		if (verbose) {
			System.out.println("concepts: "+result.getConceptCount() + " edges: "+result.getEdgeCount());

		}
		return result;
	}

}
