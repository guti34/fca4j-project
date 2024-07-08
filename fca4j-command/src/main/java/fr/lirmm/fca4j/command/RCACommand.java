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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.algo.AOC_poset_Ares;
import fr.lirmm.fca4j.algo.AOC_poset_Ceres;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.ExploRCA;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.algo.Lattice_Iceberg;
import fr.lirmm.fca4j.cli.io.RCFALReader;
import fr.lirmm.fca4j.cli.io.RCFALWriter;
import fr.lirmm.fca4j.cli.io.RCFTReader;
import fr.lirmm.fca4j.cli.io.RCFTWriter;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.JSONFormatter;

/**
 * The Class RCACommand.
 */
public class RCACommand extends Command {

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
	boolean produce_dot = false;

	/** produce extended family. */
	boolean storeExtendedFamily = false;
	
	/** produce extended family. */
	boolean storeAllExtendedFamily = false;
	
	/** add full concept extents */
	boolean fullExtents=false;

	/** add full concept extents */
	boolean fullIntents=false;

	/** rename relational attributes using concept intents. */
	boolean nameWithIntent = false;
	
	/** show all intent when renaming attributes (nameWithIntent must be set to true)*/
	boolean nameWithFullIntent = false;

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
		options.addOption(Option.builder("ra").desc("rename relational attributes using concept reduced intents").build());
		options.addOption(Option.builder("ri").desc("rename relational attributes using concept full intents").build());
		options.addOption(Option.builder("e").desc("store the final extended family").build());
		options.addOption(Option.builder("es").desc("store the extended family at each step").build());
		options.addOption(Option.builder("dot").desc("build dot files").build());
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
		produce_dot = line.hasOption("dot");
		storeXml = line.hasOption("xml");
		storeExtendedFamily = line.hasOption("e");
		storeAllExtendedFamily = line.hasOption("es");
		fullExtents=line.hasOption("fe");
		fullIntents=line.hasOption("fi");
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
		nameWithIntent = line.hasOption("ra");
		nameWithFullIntent= line.hasOption("ri");
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
		family.setNameWithIntent(nameWithIntent);
		// renaming of relational attributes full intents
		family.setNameWithFullIntent(nameWithIntent);

		String familyName = familyFile.getName();
		int index = familyName.lastIndexOf(".");
		if (index > 0)
			familyName = familyName.substring(0, index);
		Chrono chrono = new Chrono("rca");
		System.out.println("execute " + algo);
		String suffix = percent > 0 ? "" : "" + percent;
		chrono.start(algo + suffix);
		ExploRCA exploMFca = new ExploRCA(family) {

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
			boolean thisIsTheEnd = exploMFca.stopCondition() || step == maxStep;
			if (thisIsTheEnd) {
				FileWriter fw0 = new FileWriter(resultFolder.getPath() + "/step" + step + ".dot");
				exploMFca.generateDot(fw0);
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
				if (produce_dot) {
					if (thisIsTheEnd) {
						FileWriter fw = null;
						fw = new FileWriter(resultFolder.getPath() + "/step" + step + "-" + i + ".dot");
						exploMFca.generateDot(fw, cPoset, step);
					}
				} else {
					// pour garder des traces lorsque la tache ne se termine pas
					FileWriter fw = new FileWriter(resultFolder.getPath() + "/step" + step + "-" + i + ".txt");
					fw.append(msg);
					fw.close();
				}
				if (thisIsTheEnd&&storeExtendedFamily) {
					String ext_name="extended";
					switch (familyFormat) {
					case RCFAL:
						RCFALWriter.write(family, resultFolder.getPath() + "/" + familyName +ext_name+ ".rcfal");
						break;
					case RCFGZ:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name+ ".rcfgz", true);
						break;
					case RCFT:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name+ ".rcft", false);
						break;
					}
				}
				if (storeAllExtendedFamily) {
					String ext_name="_step"+step;
					switch (familyFormat) {
					case RCFAL:
						RCFALWriter.write(family, resultFolder.getPath() + "/" + familyName +ext_name+ ".rcfal");
						break;
					case RCFGZ:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name+ ".rcfgz", true);
						break;
					case RCFT:
						RCFTWriter.write(family, resultFolder.getPath() + "/" + familyName + ext_name+ ".rcft", false);
						break;
					}
				}
				
				// store concepts in json file
				if (thisIsTheEnd) {
					if (conceptArray == null) {
						// create json concept Array
						conceptArray = new JSONArray();
					}
					generateJSON(family, conceptArray, cPoset,fullIntents,fullExtents);
				}
				i++;
			}
			String msg = "time:" + chronoStep.getResult("step") + " ms\n";
			System.out.println(msg);
			results.append(msg);
			if (thisIsTheEnd) {
				if (storeXml) {
					FileWriter fw = new FileWriter(resultFolder.getPath() + "/step" + step + ".xml");
					exploMFca.generateXml(fw, family, step);
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
		String formattedString = new JSONFormatter(true, true).format(conceptArray);
		Writer json_result = new OutputStreamWriter(
				new FileOutputStream(resultFolder.getPath() + "/" + familyName + ".json"), StandardCharsets.UTF_8);
		json_result.write(formattedString);
		json_result.close();

		return null;
	}

	/**
	 * Generate JSON.
	 *
	 * @param family           the rca family
	 * @param conceptJsonArray the concept array
	 * @param conceptOrder     the concept order
	 */
	protected void generateJSON(RCAFamily family, JSONArray conceptJsonArray, IConceptOrder conceptOrder,
			boolean fullIntents, boolean fullExtents) {
		IBinaryContext matrix = conceptOrder.getContext();
		for (Iterator<Integer> it = conceptOrder.getTopDownIterator(); it.hasNext();) {
			int concept = it.next();
			JSONObject conceptJson = new JSONObject();
			conceptJson.put("id", concept);
			conceptJson.put("context", matrix.getName());
			conceptJsonArray.add(conceptJson);
			// populate reduced intent
			JSONObject attributesJson = new JSONObject();
			conceptJson.put("attributes", attributesJson);
			for (Iterator<Integer> itIntent = conceptOrder.getConceptReducedIntent(concept).iterator(); itIntent
					.hasNext();) {
				String attrName = matrix.getAttributeName(itIntent.next());
				generateAttribute(attrName, attributesJson);
			}
			if (fullIntents) {
				// populate full intent
				JSONObject fullIntentJson = new JSONObject();
				conceptJson.put("intent", fullIntentJson);
				for (Iterator<Integer> itIntent = conceptOrder.getConceptIntent(concept).iterator(); itIntent
						.hasNext();) {
					String attrName = matrix.getAttributeName(itIntent.next());
					generateAttribute(attrName, fullIntentJson);
				}
			}
			// populate extent
			JSONArray objectJsonArray = new JSONArray();
			conceptJson.put("objects", objectJsonArray);
			for (Iterator<Integer> itExtent = conceptOrder.getConceptReducedExtent(concept).iterator(); itExtent
					.hasNext();) {
				String objName = matrix.getObjectName(itExtent.next());
				objectJsonArray.add(objName);
			}
			if (fullExtents) {
				// populate full extent
				JSONArray fullExtentJsonArray = new JSONArray();
				conceptJson.put("extent", fullExtentJsonArray);
				for (Iterator<Integer> itExtent = conceptOrder.getConceptExtent(concept).iterator(); itExtent
						.hasNext();) {
					String objName = matrix.getObjectName(itExtent.next());
					fullExtentJsonArray.add(objName);
				}
			}
			// populate children
			JSONArray children = new JSONArray();
			for (Iterator<Integer> itChildren = conceptOrder.getLowerCover(concept).iterator(); itChildren.hasNext();) {
				children.add(itChildren.next());
			}
			conceptJson.put("children", children);
			// populate parents
			JSONArray parents = new JSONArray();
			for (Iterator<Integer> itParents = conceptOrder.getUpperCover(concept).iterator(); itParents.hasNext();) {
				parents.add(itParents.next());
			}
			conceptJson.put("parents", parents);
		}

	}

	private void generateAttribute(String attrName, JSONObject attributesJson) {
		int index = attrName.indexOf("(C_");
		if (index > 0) {
			String attrRelName = attrName.substring(0, index);
			JSONObject relAttrJson = (JSONObject) attributesJson.get(attrRelName);
			if (relAttrJson == null) {
				relAttrJson = new JSONObject();
				relAttrJson.put("concepts", new JSONArray());
				attributesJson.put(attrRelName, relAttrJson);
				// parse relation name
				int beg = attrRelName.indexOf("_");
				String relationName = attrRelName.substring(beg + 1);
				relAttrJson.put("relation", relationName);
				// parse operator
				String sOperator = attrRelName.substring(0, beg);
				AbstractScalingOperator operator = MyScalingOperatorFactory.createScalingOperator(sOperator, null);
				if (operator == null) {
					relAttrJson.put("operator", "?");
				} else {
					if (MyScalingOperatorFactory.hasParameter(operator.getName())) {
						relAttrJson.put("percent", MyScalingOperatorFactory.getParameter(operator.getName()));
						int idx = operator.getName().lastIndexOf('N');
						relAttrJson.put("operator", operator.getName().substring(0, idx + 1));
					} else {
						relAttrJson.put("operator", operator.getName());
					}
				}
				// parse target
				String s = attrName.substring(index + 3);
				int underscore = s.indexOf("_");
				String target = s.substring(0, underscore);
				relAttrJson.put("target", target);

			}
			// parse concepts
			String s = attrName.substring(index + 3);
			int parenthesis = s.indexOf(")");
			int underscore = s.indexOf("_");
			int numConcept = Integer.parseInt(s.substring(underscore + 1, parenthesis));
			JSONArray attrConceptJsonArray = (JSONArray) relAttrJson.get("concepts");
			attrConceptJsonArray.add(numConcept);
		} else {
			try {
				int underscore = attrName.indexOf("_");
				String key = attrName.substring(0, underscore);
				String value = attrName.substring(underscore + 1);
				attributesJson.put(key, value);
			} catch (Exception e) {
				attributesJson.put(attrName, attrName);
			}
		}

	}
}
