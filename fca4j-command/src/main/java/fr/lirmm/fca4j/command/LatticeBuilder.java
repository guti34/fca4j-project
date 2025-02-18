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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.algo.Lattice_Iceberg;
import fr.lirmm.fca4j.cli.io.ConceptOrderJSONWriter;
import fr.lirmm.fca4j.cli.io.ConceptOrderXMLWriter;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.GraphVizDotWriter;
import fr.lirmm.fca4j.util.GraphVizDotWriter.DisplayFormat;

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

	/**
	 * The Enum AlgoLattice.
	 */
	enum AlgoLattice {
		
		/** The add extent algo. */
		ADD_EXTENT, 
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
				"builds a concept lattice. ADD_EXTENT algorithm build the complete concept lattice. ICEBERG builds a lattice with only the top-most concepts of the concept lattice. A bottom concept is added that groups remaining attributes (not introduced by a frequent concept extent) and transforms the semi-lattice into a lattice. Euclidian division is used in FCA4J: e.g. In Iceberg50,  the concepts have an extent whose cardinality is >= total object count * 50 /100, where / is Euclidian division",setContext);
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
				Option.builder("a").desc("supported algorithms are:" + sb_algo_lat).hasArg().argName("ALGO").build());
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
		declareImplicationsOptions();
		// implementation
		declareImplementation(false);
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
				algo = AlgoLattice.valueOf(line.getOptionValue("a"));
			} catch (IllegalArgumentException e) {
			}
			if (algo == null)
				throw new Exception("unknown algorithm: " + line.getOptionValue("a"));
		} else
			algo = AlgoLattice.ADD_EXTENT;
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
		Chrono chrono = new Chrono("lattice");
		AbstractAlgo<ConceptOrder> lat_algo;
		switch (algo) {
		case ADD_EXTENT:
			lat_algo = new Lattice_AddExtent(ctx, chrono);
			break;
		case ICEBERG:
			lat_algo = new Lattice_Iceberg(ctx, percent, chrono);
			break;
		default:
			throw new Exception("unknown algorithm");
		}
		System.out.println("running " + algo + " (" + impl + ") data: " + inputFile.getName() + " ( " + ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		chrono.start(lat_algo.getDescription());
		lat_algo.run();
		chrono.stop(lat_algo.getDescription());
		ConceptOrder result = lat_algo.getResult();

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
				ConceptOrderXMLWriter.write(writer, result, ctx, true);
				break;
			case JSON:
				JSONObject mainJson = new JSONObject();
				mainJson.put("source", ctx.getName());
				mainJson.put("algo", lat_algo.getDescription());
//				ConceptOrderJSONWriter.build(mainJson, result, ctx);
				mainJson.put("concepts",ConceptOrderJSONWriter.build(result,false,false));
				mainJson.writeJSONString(writer);
				writer.flush();
				writer.close();
				break;
			}
		// graphviz
		if (dotFile != null) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
			boolean displaySize = true;
			boolean alignSibling = true;
			String senseLayout = "BT";
			GraphVizDotWriter dotWriter = new GraphVizDotWriter(displayMode, displaySize,false,senseLayout);
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
		// display chrono

		System.out.println("duration: " + chrono.getResult(lat_algo.getDescription()) + " ms");
		if (verbose) {
			System.out.println("concepts: "+result.getConceptCount() + " edges: "+result.getEdgeCount());

		}
		return result;
	}

}
