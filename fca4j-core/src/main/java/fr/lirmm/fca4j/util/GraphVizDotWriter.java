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
package fr.lirmm.fca4j.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.ConceptOrderFamily;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.AttributeRenamer.MODE;

/**
 * The Class GraphVizDotWriter.
 */
public class GraphVizDotWriter {

	protected final static String LINE_SEPARATOR = System.getProperty("line.separator");

	/**
	 * The Enum DisplayFormat.
	 */
	public enum DisplayFormat {
		/** The reduced. */
		SIMPLIFIED,
		/** The full. */
		FULL,
		/** The minimal. */
		MINIMAL
	}

	/** The Constant NEW_CONCEPT_COLOR. */
	public final static String NEW_CONCEPT_COLOR = "lightblue";

	/** The Constant FUSION_CONCEPT_COLOR. */
	public final static String FUSION_CONCEPT_COLOR = "orange";

	/** The display size. */
	private boolean displaySize;
	/** display concept number */
	private boolean displayConceptNumber;

	/** The use color. */
	private boolean useColor;

	/** The df. */
	private DisplayFormat df;

	/** The orientation. */
	private String orientation;

	/**
	 * conceptOrder finder to retrieve ghost concepts in RCA process
	 */
	private ConceptOrderFinder conceptOrderFinder = null;

	/**
	 * Instantiates a new graph viz dot writer.
	 *
	 * @param buff         the buff
	 * @param lattice      the lattice
	 * @param mbc          the mbc
	 * @param df           the df
	 * @param displaySize  the display size
	 * @param alignSibling the align sibling
	 * @param orientation  the orientation
	 */
	public GraphVizDotWriter(DisplayFormat df, boolean displaySize, boolean displayConceptNumber, String orientation) {
		this(df, displaySize, displayConceptNumber, orientation, null);
	}

	public GraphVizDotWriter(DisplayFormat df, boolean displaySize, boolean displayConceptNumber, String orientation,
			ConceptOrderFinder conceptOrderFinder) {
		this.df = df;
		this.displaySize = displaySize;
		this.displayConceptNumber = displayConceptNumber;
		this.useColor = true;
		this.orientation = orientation;
		this.conceptOrderFinder = conceptOrderFinder;
	}

	/**
	 * Write concept.
	 *
	 * @param sb      the sb
	 * @param concept the concept
	 * @throws IOException
	 */
	protected void writeConcept(StringBuffer sb, ConceptOrder lattice, HashMap<Integer, Integer> attrToConcept,
			Map<Integer, Double> stability, int concept, RCAFamily family, MODE mode) throws IOException {
		sb.append("" + concept + " ");
		sb.append("[shape=record,style=filled");
		if (useColor) {
			if (lattice.isNewConcept(concept))
				sb.append(",fillcolor=" + NEW_CONCEPT_COLOR);
			else if (lattice.isFusion(concept))
				sb.append(",fillcolor=" + FUSION_CONCEPT_COLOR);
		}

		sb.append(",label=\"{");

		if (displaySize) {
			append(sb, "C_" + lattice.getContext().getName() + "_" + concept);
			sb.append(" (");
			sb.append("I: " + lattice.getConceptIntent(concept).cardinality());
			sb.append(", ");
			sb.append("E: " + lattice.getConceptExtent(concept).cardinality());
			sb.append(", ");
			sb.append("Sta: " + String.format("%.3f",stability.get(concept)));
//			sb.append("Sta: " + String.format("%.3f",ConceptUtilities.computeNaiveStability(lattice.getConceptExtent(concept), lattice.getConceptIntent(concept), lattice.getContext())));
			sb.append(")");
		} else
			append(sb, "C_" + lattice.getContext().getName() + "_" + concept);

		switch (df) {
		case SIMPLIFIED:
			sb.append("|");
			for (Iterator<Integer> it2 = lattice.getConceptReducedIntent(concept).iterator(); it2.hasNext();) {
				int numattr = it2.next();
				String attrName = lattice.getContext().getAttributeName(numattr);
				if (mode == MODE.SIMPLE)
					sb.append(attrName + "\\n");
				else {
//					System.out.println("rename:"+attrName);
					sb.append(AttributeRenamer.build(family, attrName, mode, concept, conceptOrderFinder) + "\\n");
				}
			}
			sb.append("|");
			for (Iterator<Integer> it2 = lattice.getConceptReducedExtent(concept).iterator(); it2.hasNext();)
				sb.append(lattice.getContext().getObjectName(it2.next()) + "\\n");
			break;
		case FULL:
			sb.append("|");
			ISet rIntent = lattice.getConceptReducedIntent(concept);
			ISet remainingIntent = lattice.getConceptIntent(concept).clone();
			remainingIntent.removeAll(rIntent);
			// display reduced intent
			for (Iterator<Integer> it2 = rIntent.iterator(); it2.hasNext();) {
				int numattr = it2.next();
				String attrName = lattice.getContext().getAttributeName(numattr);
				if (mode == MODE.SIMPLE)
					sb.append(attrName + "\\n");
				else {
					int stopConcept = attrToConcept.get(numattr);
					sb.append(AttributeRenamer.build(family, attrName, mode, stopConcept, conceptOrderFinder) + "\\n");
				}
			}
			if (!remainingIntent.isEmpty())
				sb.append("_INH_ATT_" + "\\n");
			// display inherited intent
			for (Iterator<Integer> it2 = remainingIntent.iterator(); it2.hasNext();) {
				int numattr = it2.next();
				String attrName = lattice.getContext().getAttributeName(numattr);
				if (mode == MODE.SIMPLE)
					sb.append(attrName + "\\n");
				else {
					int stopConcept = attrToConcept.get(numattr);
					sb.append(AttributeRenamer.build(family, attrName, mode, stopConcept, conceptOrderFinder) + "\\n");
				}
			}

			sb.append("|");
			ISet rExtent = lattice.getConceptReducedExtent(concept);
			ISet remainingExtent = lattice.getConceptExtent(concept).clone();
			remainingExtent.removeAll(rExtent);
			// display reduced extent
			for (Iterator<Integer> it2 = rExtent.iterator(); it2.hasNext();) {
				int numobj = it2.next();
				String objName = lattice.getContext().getObjectName(numobj);
				sb.append(objName + "\\n");
			}
			if (!remainingExtent.isEmpty())
				sb.append("_INH_OBJ_" + "\\n");
			// display inherited extent
			for (Iterator<Integer> it2 = remainingExtent.iterator(); it2.hasNext();) {
				int numobj = it2.next();
				String objName = lattice.getContext().getObjectName(numobj);
				sb.append(objName + "\\n");
			}
			break;
		case MINIMAL:
			sb.append("\\n");
			break;
		}

		sb.append("}\"];\n");

	}

	/**
	 * Builds the dot concepts.
	 *
	 * @param sb the sb
	 * @throws IOException
	 */
	protected void buildOrder(StringBuffer sb, ConceptOrder lattice) throws IOException {
		// build a map from attributes to original concept
		HashMap<Integer, Integer> attrToConcept = new HashMap<>();
		for (int numconcept : lattice.getConcepts()) {
			for (Iterator<Integer> it = lattice.getConceptReducedIntent(numconcept).iterator(); it.hasNext();)
				attrToConcept.put(it.next(), numconcept);
		}
		// compute stability for each concepts
		Map<Integer, Double> stability = ConceptUtilities.computeStability(lattice);
		for (Iterator<Integer> it = lattice.getBasicIterator(); it.hasNext();) {
			int concept = it.next();
			writeConcept(sb, lattice, attrToConcept, stability, concept, null, MODE.SIMPLE);
		}
		for (Iterator<Integer> it = lattice.getBasicIterator(); it.hasNext();) {
			int c = it.next();
			for (Iterator<Integer> childIterator = lattice.getUpperCoverIterator(c); childIterator.hasNext();) {
				int child = childIterator.next();
				sb.append("\t" + c + " -> " + child + "\n");
			}
		}

	}

	private void buildOrder(StringBuffer buffer, RCAFamily family, ConceptOrder conceptOrder,
			boolean displayConceptNumber, MODE mode) throws IOException {
		// build a map from attributes to original concept
		HashMap<Integer, Integer> attrToConcept = new HashMap<>();
		for (int numconcept : conceptOrder.getConcepts()) {
			for (Iterator<Integer> it = conceptOrder.getConceptReducedIntent(numconcept).iterator(); it.hasNext();)
				attrToConcept.put(it.next(), numconcept);
		}
		Map<Integer, Double> stability = ConceptUtilities.computeStability(conceptOrder);

		appendLine(buffer, "subgraph ", conceptOrder.getContext().getName(), " { ");
		appendLine(buffer, "label=\"", conceptOrder.getContext().getName(), "\";");

		for (Iterator<Integer> itConcept = conceptOrder.getBasicIterator(); itConcept.hasNext();) {
			int c = itConcept.next();
			writeConcept(buffer, conceptOrder, attrToConcept, stability, c, family, mode);
		}

		for (Iterator<Integer> itConcept = conceptOrder.getBasicIterator(); itConcept.hasNext();) {
			int c = itConcept.next();
			Iterator<Integer> itc = conceptOrder.getLowerCoverIterator(c);
			while (itc.hasNext()) {
				appendLine(buffer, "\t", Integer.toString(itc.next()), " -> ", "" + c);
			}
		}
		appendLine(buffer, "}");

	}

	/**
	 * Write.
	 */
	public void write(Writer buff, ConceptOrder order) {
		try {
			StringBuffer sb = new StringBuffer();
			sb.append("digraph G { \n");
			sb.append("\trankdir=" + orientation + ";\n");
			buildOrder(sb, order);

			sb.append("}");
			buff.write(sb.toString());
			buff.flush();
			buff.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Write.
	 */
	public void write(Writer buff, RCAFamily family, ConceptOrderFamily conceptOrderFamily,
			boolean displayConceptNumber, AttributeRenamer.MODE renameMode) {
		try {
			StringBuffer sb = new StringBuffer();
			sb.append("digraph G { \n");
			sb.append("\trankdir=" + orientation + ";\n");
			for (ConceptOrder conceptOrder : conceptOrderFamily.getConceptOrders())
				buildOrder(sb, family, conceptOrder, displayConceptNumber, renameMode);

			sb.append("}");
			buff.write(sb.toString());
			buff.flush();
			buff.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Appends the given string to the internal buffer.
	 *
	 * @param cs the cs
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static void append(StringBuffer buffer, CharSequence... cs) throws IOException {
		for (CharSequence s : cs)
			buffer.append(s);
	}

	/**
	 * Appends the given string to a dedicated line in the internal buffer.
	 *
	 * @param s a string.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static void appendLine(StringBuffer buffer, CharSequence... s) throws IOException {
		append(buffer, s);
		newLine(buffer);

	}

	/**
	 * Appends an empty line in the internal buffer.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static void newLine(StringBuffer buffer) throws IOException {
		append(buffer, LINE_SEPARATOR);
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	public String getDescription() {
		return "DOT Writer";
	}

}
