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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.json.simple.JSONArray;

import fr.lirmm.fca4j.algo.AOC_poset_Ares;
import fr.lirmm.fca4j.algo.AOC_poset_Ceres;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.ExploRCA;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.algo.Lattice_Iceberg;
import fr.lirmm.fca4j.cli.io.ConceptOrderJSONWriter;
import fr.lirmm.fca4j.cli.io.FamilyXMLWriter;
import fr.lirmm.fca4j.cli.io.RCFALReader;
import fr.lirmm.fca4j.cli.io.RCFALWriter;
import fr.lirmm.fca4j.cli.io.RCFTReader;
import fr.lirmm.fca4j.cli.io.RCFTWriter;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.AttributeRenamer;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.GraphVizDotWriter;
import fr.lirmm.fca4j.util.GraphVizDotWriter.DisplayFormat;
import fr.lirmm.fca4j.util.JSONFormatter;

/**
 * The Class RCACommand.
 */
public class RCACommand extends Command {
	protected boolean clean = false;

	/** The family file. */
	protected File familyFile;

	/** The result folder. */
	protected File resultFolder;

	/** The new name. */
	protected String newName;

	/** The family format. */
	protected FamilyFormat familyFormat;

	/** The algorithm. */
	protected AlgoRCA algo;

	/** The percent. */
	protected int percent = -1;

	/** produce dot file for graphviz. */
	boolean produceDot = false;

	/** The display mode. */
	DisplayFormat displayMode=DisplayFormat.SIMPLIFIED;
	
	/** storeAllJSon */
	boolean produceJSon = false;

	/** produce extended family. */
	boolean storeExtendedFamily = false;

	/** produce extended family. */
	boolean storeAllExtendedFamily = false;

	/** add full concept extents */
	boolean fullExtents = false;

	/** add full concept extents */
	boolean fullIntents = false;

	/** rename relational attributes using concept reduced intents. */
	boolean nameWithReducedIntent = false;

	/** rename relational attributes using concept full intents. */
	boolean nameWithFullIntent = false;

	/**
	 * rename relational attributes using concept reduced intents except for object
	 * concepts (when reduced intent is empty).
	 */
	boolean nameWithReducedIntent2 = false;

	/**
	 * combined with renaming options to build intent only with native attributes
	 */
	boolean nativeOnly = false;

	/** store xml. */
	boolean storeXml = false;

	/** The max step. */
	int maxStep = -1;

	/**
	 * The Enum AlgoRCA.
	 */
	enum AlgoRCA {

		/** The ares algorithm. */
		ARES,
		/** The ceres algorithm. */
		CERES,
		/** The pluton algorithm. */
		PLUTON,
		/** The hermes algorithm. */
		HERMES,
		/** The add extent algorithm. */
		ADD_EXTENT,
		/** The iceberg algorithm. */
		ICEBERG
	};

	/**
	 * Instantiates a new RCA command.
	 *
	 * @param setContext the set context
	 */
	public RCACommand(ISetContext setContext) {
		super("rca", "to create a conceptual structure family from a relational context family. "
				+ "The output is a JSON file that can be opened in RCAviz, a DOT file that contains the graph of the conceptual structure family at the end of the process,"
				+ "TXT files for tracing the size of structures at each step, and traces.csv that contains the formal and relational contexts and other settings used at each step."
				+ "The input is a relational context family.", "input", "output-folder", setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		StringBuilder sb_algo_aoc = new StringBuilder();
		for (AlgoRCA algo : AlgoRCA.values()) {
			sb_algo_aoc.append("\n* " + algo.name());
		}
		options.addOption(
				Option.builder("clean").desc("remove relational attributes concerning disappeared target concepts. Be careful, this option can cause the algorithm to loop").build());
		options.addOption(
				Option.builder("ra").desc("rename relational attributes using concept reduced intents").build());
		options.addOption(Option.builder("rai").desc(
				"rename relational attributes using concept full intents only when reduced intent is empty (object concepts)")
				.build());
		options.addOption(Option.builder("ri").desc("rename relational attributes using concept full intents").build());
		options.addOption(Option.builder("na").desc(
				"this option must be combined with a renaming option, it limits the construction of intents to native (non-relational) attributes")
				.build());
		options.addOption(Option.builder("x").desc("stop execution after LIMIT steps")
				.hasArg().argName("LIMIT").build());
		
		options.addOption(Option.builder("e").desc("store the final extended family").build());
		options.addOption(Option.builder("es").desc("store the extended family at each step").build());
		options.addOption(Option.builder("dot").desc("build dot files at each step").build());
		options.addOption(Option.builder("d")// .longOpt("format")
				.desc("display format of the concepts for GraphViz. Available formats are:\n* FULL\n* SIMPLIFIED (default)\n* MINIMAL")
				.hasArg().argName("DISPLAY-MODE").build());
		options.addOption(Option.builder("json").desc("build json files at each step").build());
		options.addOption(Option.builder("xml").desc("build xml files").build());
		options.addOption(Option.builder("fe").desc("add full concept extents").build());
		options.addOption(Option.builder("fi").desc("add full concept intents").build());
		// algo
		options.addOption(
				Option.builder("a").desc("supported algorithms are:" + sb_algo_aoc).hasArg().argName("ALGO").build());
		// percent
		options.addOption(Option.builder("p").desc("for ICEBERG: percentage (of extent) to keep the top-most concepts")
				.hasArg().argName("PERCENT").build());
		// family format
		declareFamilyFormat("f", "FAMILY-FORMAT");
		// common options
		declareCommon();

	}

	/**
	 * Check options.
	 *
	 * @param line the command line
	 * @throws Exception the exception
	 */
	@Override
	public void checkOptions(CommandLine line) throws Exception {
		clean = line.hasOption("clean");
		nativeOnly = line.hasOption("na");
		produceJSon = line.hasOption("json");
		produceDot = line.hasOption("dot");
		storeXml = line.hasOption("xml");
		storeExtendedFamily = line.hasOption("e");
		storeAllExtendedFamily = line.hasOption("es");
		fullExtents = line.hasOption("fe");
		fullIntents = line.hasOption("fi");
		// family file
		List<String> args = line.getArgList();
		for (String arg : args) {
			if (name().equalsIgnoreCase(arg)) {
				args.remove(arg);
				break;
			}
		}
		if (args.size() < 1)
			throw new Exception("family file missing");
		String inputFileName = args.get(0);
		familyFile = new File(inputFileName);
		if (!familyFile.exists())
			throw new Exception("the specified family file path is not found: " + inputFileName);
		familyFormat = checkFamilyFormat(line, inputFileName, "f");
		if (args.size() < 2)
			throw new Exception("output folder missing");
		String folderPath = args.get(1);
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
		
		if (line.hasOption("a")) {
			try {
				algo = AlgoRCA.valueOf(line.getOptionValue("a"));
			} catch (IllegalArgumentException e) {
			}
			if (algo == null)
				throw new Exception("unknown algorithm: " + line.getOptionValue("a"));
			else if (algo == AlgoRCA.ICEBERG) {
				if (!line.hasOption("p"))
					throw new Exception(
							"a percentage must be specified as a parameter for ICEBERG algorithm (-p option)");
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

		} else
			throw new Exception("algorithm must be specified (-a option)");
		try{
			if(line.hasOption("d"))
				displayMode=DisplayFormat.valueOf(line.getOptionValue("d").toUpperCase());
		}catch(Exception e){
			throw new Exception("display mode option not recognized: " + line.getOptionValue("d"));
		}
		if(line.hasOption("x"))
		{
		try {
			String maxStepString=line.getOptionValue("x");
			maxStep = Integer.parseInt(maxStepString);
			if (maxStep <= 0)
				throw new Exception();
		} catch (Exception e) {
			throw new Exception(
					"invalid parameter for stopping execution after LIMIT steps  (-x option) specify a positive integer");
		}
		}
		else if(clean)
		{
			throw new Exception(
					"The -clean option can cause an infinite loop. For this you must use the -x option to limit the number of steps in the algorithm.");			
		}
		nameWithReducedIntent = line.hasOption("ra");
		nameWithFullIntent = line.hasOption("ri");
		if (nameWithFullIntent)
			nameWithReducedIntent = false;
		nameWithReducedIntent2 = line.hasOption("rai");
	}
	/**
	 * Declare Graphviz DOT options.
	 */
	protected void declareGraphvizOptions() {
		options.addOption(Option.builder("d")// .longOpt("format")
				.desc("display format of the concepts for GraphViz. Available formats are:\n* FULL\n* SIMPLIFIED (default)\n* MINIMAL")
				.hasArg().argName("DISPLAY-MODE").build());
	}


	/**
	 * Exec.
	 *
	 * @return the resulting object
	 * @throws Exception the exception
	 */
	@Override
	public Object exec() throws Exception {
		// public static void compute(PrintWriter output, Chrono chrono, String
		// path, RCAFamily rcf, String algo, Integer param, int maxStep, boolean
		// produce_dot,boolean storeExtendedFamily,boolean
		// nameRelAttrWithIntent) {
		RCAFamily family;
		if (familyFile.exists()) {
			switch (familyFormat) {
			case RCFAL:
				family = RCFALReader.read(familyFile.getPath());
				break;
			case RCFGZ:
				family = RCFTReader.read(familyFile.getPath(), true);
				break;
			case RCFT:
			default:
				family = RCFTReader.read(familyFile.getPath(), false);
				break;

			}
		} else {
			throw new Exception("input file " + familyFile.getPath() + " cannot be found");
		}
		// renaming of relational attributes reduced intents
		family.setNameWithReducedIntentRA(nameWithReducedIntent);
		// renaming of relational attributes reduced intents and inherited attributes
		// for object concepts
		family.setNameWithReducedIntentRAI(nameWithReducedIntent2);
		// renaming of relational attributes full intents
		family.setNameWithFullIntentRI(nameWithFullIntent);
		// native only
		family.setNativeOnly(nativeOnly);

		String familyName = familyFile.getName();
		int index = familyName.lastIndexOf(".");
		if (index > 0)
			familyName = familyName.substring(0, index);
		Chrono chrono = new Chrono("rca");
		System.out.println("execute " + algo);
		String suffix = percent > 0 ? "" : "" + percent;
		chrono.start(algo + suffix);
		ExploRCA exploMFca = new ExploRCA(family,clean) {

			@Override
			protected AbstractAlgo<ConceptOrder> createAlgo(IBinaryContext context, int numstep) {
				switch (algo) {
				case ADD_EXTENT:
					return new Lattice_AddExtent(context, chrono);
				case ICEBERG:
					return new Lattice_Iceberg(context, percent, chrono);
				case HERMES:
					return new AOC_poset_Hermes(context, chrono);
				case PLUTON:
					return new AOC_poset_Pluton(context, chrono);
				case ARES:
					return new AOC_poset_Ares(context, chrono, null, true, true);
				case CERES:
					return new AOC_poset_Ceres(context, chrono);
				default:
					return null;
				}
			}
		};
		// construction
		int step = 0;
		HashMap<String, Integer> initAttrs = new HashMap<>();
		StringBuilder trace = new StringBuilder();
		StringBuilder results = new StringBuilder();
		JSONArray conceptArray = null;
		while (!exploMFca.isEnd()) {

			// on trace l'�tape courante
			trace.append("\n" + exploMFca.getNumStep() + "\n");
			trace.append("OAContexts\n");
			for (FormalContext c : family.getFormalContexts()) {
				String algo2 = algo.name();
				trace.append(c.getName() + "," + algo2 + (percent > 0 ? "," + percent : "") + "\n");
			}
			trace.append("OOContexts\n");
			for (RelationalContext rc : family.getRelationalContexts()) {
				AbstractScalingOperator scaling = rc.getOperator();
				trace.append(rc.getRelationName() + "," + scaling.getName() + "\n");

			}
			// on calcule l'�tape
			Chrono chronoStep = new Chrono("ChronoStep");
			chronoStep.start("step");
			exploMFca.computeStep();
			chronoStep.stop("step");
			boolean thisIsTheEnd = exploMFca.stopCondition() || step == maxStep-1;
			AttributeRenamer.MODE mode = AttributeRenamer.MODE.SIMPLE;
			if (nameWithFullIntent) {
				if (nativeOnly)
					mode = AttributeRenamer.MODE.FULL_INTENT_NA;
				else
					mode = AttributeRenamer.MODE.FULL_INTENT;
			} else if (nameWithReducedIntent) {
				if (nativeOnly)
					mode = AttributeRenamer.MODE.REDUCED_INTENT_NA;
				else
					mode = AttributeRenamer.MODE.REDUCED_INTENT;
			} else if (nameWithReducedIntent2) {
				if (nativeOnly)
					mode = AttributeRenamer.MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA;
				else
					mode = AttributeRenamer.MODE.REDUCED_INTENT_FULL_WHEN_EMPTY;
			}
			if (thisIsTheEnd || produceDot) {
				FileWriter fw0 = new FileWriter(resultFolder.getPath() + "/step" + step + ".dot");
				String senseLayout = "BT";
				GraphVizDotWriter dotWriter = new GraphVizDotWriter(displayMode, false,false,senseLayout,exploMFca.createConceptFinder());
				dotWriter.write(fw0, family,exploMFca.getConceptOrderFamily(),true,mode );
			}
			int i = 0;
			ArrayList<ConceptOrder> list_concept_orders = exploMFca.getConceptOrderFamily().getConceptOrders();
			for (ConceptOrder cPoset : list_concept_orders) {
				String key = cPoset.getContext().getName();
				if (initAttrs.get(key) == null) {
					initAttrs.put(key, cPoset.getContext().getAttributeCount());
				}
				int nbAttrib = cPoset.getContext().getAttributeCount() - initAttrs.get(key);
				String msg = cPoset.getContext().getName() + ": step=" + (step + 1) + " nb.concepts="
						+ cPoset.getConceptCount() + " nb.attrib rel.=" + nbAttrib + "\n";
				System.out.println(msg);
				results.append(msg);
				// pour garder des traces lorsque la tache ne se termine pas
				FileWriter fw = new FileWriter(resultFolder.getPath() + "/step" + step + "-" + i + ".txt");
				fw.append(msg);
				fw.close();
				if (thisIsTheEnd && storeExtendedFamily) {
					String ext_name = "extended";
					switch (familyFormat) {
					case RCFAL:
						RCFALWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name + ".rcfal",
								mode,exploMFca.createConceptFinder());
						break;
					case RCFGZ:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name + ".rcfgz", true,
								mode,exploMFca.createConceptFinder());
						break;
					case RCFT:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name + ".rcft", false,
								mode,exploMFca.createConceptFinder());
						break;
					}
				}
				if (storeAllExtendedFamily) {
					String ext_name = "_step" + step;
					switch (familyFormat) {
					case RCFAL:
						RCFALWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name + ".rcfal",
								mode,exploMFca.createConceptFinder());
						break;
					case RCFGZ:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name + ".rcfgz", true,
								mode,exploMFca.createConceptFinder());
						break;
					case RCFT:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name + ".rcft", false,
								mode,exploMFca.createConceptFinder());
						break;
					}
				}

				// store concepts in json file
				if (thisIsTheEnd) {
					if (conceptArray == null) {
						// create json concept Array
						conceptArray = new JSONArray();
					}
					conceptArray.addAll(ConceptOrderJSONWriter.build(cPoset, fullIntents, fullExtents));
//					generateJSON(family, conceptArray, cPoset,fullIntents,fullExtents);
				}
				i++;
			}
			String msg = "time:" + chronoStep.getResult("step") + " ms\n";
			System.out.println(msg);
			results.append(msg);
			if (produceJSon) {
				writeJSon("step" + step, conceptArray);
			}
			if (thisIsTheEnd) {
				if (storeXml) {
					FileWriter fw = new FileWriter(resultFolder.getPath() + "/step" + step + ".xml");
					
					FamilyXMLWriter genXml = new FamilyXMLWriter(family, exploMFca.getConceptOrderFamily(), step, true);
					genXml.generate();
					genXml.writeDocument(fw, "UTF-8");
					fw.close();
//					exploMFca.generateXml(fw, family, step);
				}

				exploMFca.setEnd(true);
			}
			step++;
		}
		chrono.stop(algo.toString() + suffix);
		results.append("total time: " + chrono.getResult(algo.toString() + suffix));
		// save trace
		FileWriter fw_trace = new FileWriter(resultFolder.getPath() + "/trace.csv");
		fw_trace.write(trace.toString());
		fw_trace.close();
		// save results
		FileWriter fw_result = new FileWriter(resultFolder.getPath() + "/results.txt");
		fw_result.write(results.toString());
		fw_result.close();
		// save json file
		writeJSon(familyName, conceptArray);

		return null;
	}

	private void writeJSon(String name, JSONArray conceptArray) throws IOException {
		String formattedString = new JSONFormatter(true, true).format(conceptArray);
		Writer json_result = new OutputStreamWriter(new FileOutputStream(resultFolder.getPath() + "/" + name + ".json"),
				StandardCharsets.UTF_8);
		json_result.write(formattedString);
		json_result.close();
	}
}
