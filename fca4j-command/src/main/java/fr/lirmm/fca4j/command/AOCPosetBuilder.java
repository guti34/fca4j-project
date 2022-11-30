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
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.algo.AOC_poset_Ares;
import fr.lirmm.fca4j.algo.AOC_poset_Athena;
import fr.lirmm.fca4j.algo.AOC_poset_Ceres;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.cli.io.ConceptOrderJSONWriter;
import fr.lirmm.fca4j.cli.io.ConceptOrderXMLWriter;
import fr.lirmm.fca4j.cli.io.GraphVizDotWriter;
import fr.lirmm.fca4j.cli.io.GraphVizDotWriter.DisplayFormat;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;

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
		// implementation
		declareImplementation(false);
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
		AbstractAlgo<ConceptOrder> aoc_algo;
		switch (algo) {
//		case ATHENA:
//			aoc_algo = new AOC_poset_Athena(ctx, chrono);
//			break;
		case HERMES:
			aoc_algo = new AOC_poset_Hermes(ctx, chrono);
			break;
		case PLUTON:
			aoc_algo = new AOC_poset_Pluton(ctx, chrono);
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
		System.out.println("running " + algo + " (" + impl + ") data: " + inputFile.getName() + " ( " + ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		chrono.start(aoc_algo.getDescription());
		aoc_algo.run();
		chrono.stop(aoc_algo.getDescription());
		ConceptOrder result = aoc_algo.getResult();

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
				mainJson.put("algo", aoc_algo.getDescription());
				ConceptOrderJSONWriter.build(mainJson, result, ctx);
				mainJson.writeJSONString(writer);
				writer.flush();
				writer.close();
				break;
			}
		// graphviz
		if (dotFile != null) {
			BufferedWriter bw = new BufferedWriter(new FileWriter(dotFile));
			DisplayFormat df = getDisplayMode();
			boolean displaySize = true;
			boolean alignSibling = true;
			String senseLayout = "BT";
			GraphVizDotWriter dotWriter = new GraphVizDotWriter(bw, result, result.getContext(), df, displaySize,
					alignSibling, senseLayout);
			dotWriter.write();
		}
		// display chrono
		System.out.println("duration: " + chrono.getResult(aoc_algo.getDescription()) + " ms");
		if (verbose) {
			System.out.println("done");
			System.out.println("concepts: "+result.getConceptCount() + " edges: "+result.getEdgeCount());

		}
		return result;
	}

}
