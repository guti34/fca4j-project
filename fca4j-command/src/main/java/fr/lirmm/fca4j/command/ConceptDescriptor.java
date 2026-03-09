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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.algo.AOC_poset_Ares;
import fr.lirmm.fca4j.algo.AOC_poset_Ceres;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.algo.Lattice_AddIntent;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.DlgpUtils;

public class ConceptDescriptor extends ConceptOrderBuilder {
	/** The input file. */
	protected File inputFile;

	/** The binary context. */
	protected IBinaryContext ctx;

	/** The input format. */
	protected ContextFormat inputFormat;

	/**
	 * The Enum AlgoAOCPoset.
	 */
	enum AlgoConceptOrder {

		/** The ares algorithm. */
		ARES,
		/** The ceres algorithm. */
		CERES,
		/** The pluton algorithm. */
		PLUTON,
		/** The hermes algorithm. */
		HERMES,
		/** The add_extent algorithm. */
		ADD_EXTENT,
		/** The add_intent algorithm. */
		ADD_INTENT
	};

	/** The algo. */
	protected AlgoConceptOrder algoType;
	/** The result folder. */
	protected File resultFolder;
	/** concept order */
	protected ConceptOrder order;

	public ConceptDescriptor(ISetContext setContext) {
		super("concept_descriptor",
				"build a concept order and provides datalog files describing each concept (experimental)", setContext);
	}

	@Override
	void createOptions() {
		StringBuilder sb_algo = new StringBuilder();
		boolean first = true;
		for (AlgoConceptOrder algo : AlgoConceptOrder.values()) {
			sb_algo.append("\n* " + algo.name());
			if (first) {
				first = false;
			}
			if (algo == AlgoConceptOrder.ADD_EXTENT)
				sb_algo.append(" (default)");
		}
		// algo
		options.addOption(
				Option.builder("a").desc("supported algorithms are:" + sb_algo).hasArg().argName("ALGO").build());
		options.addOption(Option.builder("folder").desc("folder to generate a file by concept for results").hasArg()
				.argName("PATH").build());
		// input format
		declareContextFormat("i", "INPUT-FORMAT");
		// implementation
		declareImplementation(false);
		// common options
		declareCommon();
	}

	@Override
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
		if (!line.hasOption("folder"))
			throw new Exception("input file missing");
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

		checkImplementation(line);
		if (line.hasOption("a")) {
			try {
				algoType = AlgoConceptOrder.valueOf(line.getOptionValue("a").toUpperCase());
			} catch (IllegalArgumentException e) {
			}
			if (algoType == null)
				throw new Exception("unknown algorithm: " + line.getOptionValue("a"));
		} else
			algoType = AlgoConceptOrder.ADD_EXTENT;
		// separator
		checkSeparator(line);
		// verbose
		checkVerbose(line);
	}

	@Override
	public Object exec() throws Exception {
		ctx = readContext(inputFormat, inputFile);
		String contextFileName = getFileNameWithoutExtension(inputFile);
		Chrono chrono = new Chrono("concept_descriptor");
		AbstractAlgo<ConceptOrder> algo;
		switch (algoType) {
		case HERMES:
			algo = new AOC_poset_Hermes(ctx, chrono);
			break;
		case PLUTON:
			algo = new AOC_poset_Pluton(ctx, chrono);
			break;
		case ARES:
			algo = new AOC_poset_Ares(ctx, chrono, null, true, true);
			break;
		case CERES:
			algo = new AOC_poset_Ceres(ctx, chrono);
			break;
		case ADD_EXTENT:
			algo = new Lattice_AddExtent(ctx, chrono);
			break;
		case ADD_INTENT:
			algo = new Lattice_AddIntent(ctx, chrono);
			break;
		default:
			throw new Exception("unknown algorithm");
		}
		System.out.println("running " + algo + " (" + impl + ") data: " + inputFile.getName() + " ( "
				+ ctx.getObjectCount() + " x " + ctx.getAttributeCount() + " )");
		chrono.start(algo.getDescription());
		algo.run();
		chrono.stop(algo.getDescription());
		order = algo.getResult();
		String[] attrNames=new String[order.getContext().getAttributeCount()];
				for(int attr=0;attr<order.getContext().getAttributeCount();attr++)
			attrNames[attr]=order.getContext().getAttributeName(attr);
		
		for (int concept : order.getConcepts()) {
			FileWriter fileWriter = new FileWriter(
					resultFolder.getPath() + File.separator + contextFileName + "Concept" + concept + ".datalog");
			PrintWriter printWriter = new PrintWriter(fileWriter);
			printWriter.append("% Concept description of concept " + concept + " from " + contextFileName);
			printWriter.append("\n");
			// assertion with main conjunction: reduced intent or intent if empty
			ISet factConjunction = order.getConceptReducedIntent(concept);
			if (factConjunction.isEmpty())
				factConjunction = order.getConceptIntent(concept);
			if (!factConjunction.isEmpty()) {
				printWriter.append("@Facts\n");
				printWriter.append(DlgpUtils.buildConjunction(factConjunction,attrNames));
				printWriter.append(".\n");
			}
			// constraints with intent of siblings
			ISet siblingsIntent = factory.createSet();
			ISet visited = factory.createSet();
			for (Iterator<Integer> it = order.getLowerCoverIterator(concept); it.hasNext();) {
				int parent = it.next();
				findImmediateSuccessorsIntent( order.getConceptIntent(concept),parent, siblingsIntent,visited);
			}
			for (Iterator<Integer> it = order.getUpperCoverIterator(concept); it.hasNext();) {
				int child = it.next();
				findImmediatePredecessorsIntent( order.getConceptIntent(concept),child, siblingsIntent,visited);
			}
			if (!siblingsIntent.isEmpty()) {
				printWriter.append("@Constraints\n");
				int count = 0;
				for (Iterator<Integer> it = siblingsIntent.iterator(); it.hasNext();) {
					ISet constraint = factory.createSet();
					constraint.add(it.next());
					printWriter.append("[constraint" + (++count) + "] ");
					printWriter.append("! :- ");
				printWriter.append(DlgpUtils.buildConjunction(constraint,attrNames));
					printWriter.append(".\n");
				}

			}
			// rules with intent of successors
			ISet successorsIntent = factory.createSet();
			visited = factory.createSet();
			findImmediateSuccessorsIntent(factory.createSet(), concept, successorsIntent,visited);
			successorsIntent.removeAll(factConjunction);
			if (!successorsIntent.isEmpty()) {
				printWriter.append("@Rules\n");
				int count = 0;
				for (Iterator<Integer> it = successorsIntent.iterator(); it.hasNext();) {
					ISet conclusion = factory.createSet();
					conclusion.add(it.next());
					printWriter.append("[rule" + (++count) + "] ");
					printWriter.append(DlgpUtils.buildConjunction(conclusion,attrNames));
					printWriter.append(":- ");
					printWriter.append(DlgpUtils.buildConjunction(factConjunction,attrNames));
					printWriter.append(".\n");
				}
			}
			printWriter.flush();
			printWriter.close();
		}
		return order;
	}
	private void findImmediatePredecessorsIntent(ISet cIntent,int current_concept, ISet immediatePredecessorsIntent, ISet visited) {
		for (Iterator<Integer> it = order.getLowerCoverIterator(current_concept); it.hasNext();) {
			int pred = it.next();
			if (!visited.contains(pred)) {
				visited.add(pred);
				ISet reducedIntent = order.getConceptReducedIntent(pred);
				if (!reducedIntent.isEmpty() && ! cIntent.intersects(order.getConceptReducedIntent(pred))) {
					immediatePredecessorsIntent.addAll(reducedIntent);
				} else {
					findImmediatePredecessorsIntent(cIntent,pred, immediatePredecessorsIntent,visited);
				}
			}
		}
	}
	private void findImmediateSuccessorsIntent(ISet cIntent,int current_concept, ISet immediateSuccessorsIntent, ISet visited) {
		for (Iterator<Integer> it = order.getUpperCoverIterator(current_concept); it.hasNext();) {
			int succ = it.next();
			if (!visited.contains(succ)) {
				visited.add(succ);
				ISet reducedIntent = order.getConceptReducedIntent(succ);
				if (!reducedIntent.isEmpty() && ! cIntent.intersects(order.getConceptReducedIntent(succ))) {
					immediateSuccessorsIntent.addAll(reducedIntent);
				} else {
					findImmediateSuccessorsIntent(cIntent,succ, immediateSuccessorsIntent,visited);
				}
			}
		}
	}


	public static String getFileNameWithoutExtension(File file) {
		String name = file.getName();
		int dotIndex = name.lastIndexOf('.');
		return (dotIndex > 0) ? name.substring(0, dotIndex) : name;
	}
}
