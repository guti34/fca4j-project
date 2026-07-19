/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;

/**
 * The Class ConceptOrderXMLWriter.
 */
public class ConceptOrderXMLWriter {

	/**
	 * Write.
	 *
	 * @param buff    the buff
	 * @param order   the order
	 * @param mbc     the mbc
	 * @param reduced the reduced
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void write(BufferedWriter buff, IConceptOrder order, IBinaryContext mbc)
			throws IOException {

		ISet setOfAllObjects = mbc.getFactory().createSet();
		int cpt;

		for (Iterator<Integer> it = order.getMaximals().iterator(); it.hasNext();) {
			cpt = it.next();
			setOfAllObjects.addAll(order.getConceptExtent(cpt));
		}

		ISet setOfAllAttributes = mbc.getFactory().createSet();
		for (Iterator<Integer> it = order.getMinimals().iterator(); it.hasNext();) {
			cpt = it.next();
			setOfAllAttributes.addAll(order.getConceptIntent(cpt));
		}

		buff.write("<GSH numberObj=\"" + mbc.getObjectCount()
				+ "\" numberAtt=\"" + mbc.getAttributeCount()
				+ "\" numberCpt=\"" + order.getConceptCount() + "\">\n");

		buff.write("<Name>" + order.getId() + "</Name>\n");

		writeConceptualStructure(buff, order, mbc);

		buff.write("</GSH>\n");
		buff.flush();
	}

	/**
	 * Write conceptual structure.
	 *
	 * @param buff    the buff
	 * @param order   the order
	 * @param mbc     the mbc
	 * @param reduced the reduced
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	static protected void writeConceptualStructure(BufferedWriter buff, IConceptOrder order, IBinaryContext mbc) throws IOException {
		int fo;
		int fa;

		for (Iterator<Integer> it_TopDown = order.getTopDownIterator(); it_TopDown.hasNext();) {

			int cpt = it_TopDown.next();

			buff.write("<Concept>\n");
			buff.write("<ID> " + cpt + " </ID>\n");

			buff.write("<Extent>\n");
			Iterator<Integer> it_extent;
			it_extent = order.getConceptReducedExtent(cpt).iterator();
			while (it_extent.hasNext()) {
				fo = it_extent.next();
				buff.write("<Object_Ref>");
				buff.write(mbc.getObjectName(fo));
				buff.write("</Object_Ref>\n");
			}
			buff.write("</Extent>\n");

			buff.write("<Intent>\n");
			Iterator<Integer> it_intent;
			it_intent = order.getConceptReducedIntent(cpt).iterator();
			while (it_intent.hasNext()) {
				fa = it_intent.next();
				buff.write("<Attribute_Ref>");
				buff.write(mbc.getAttributeName(fa));
				buff.write("</Attribute_Ref>\n");
			}
			buff.write("</Intent>\n");

			buff.write("<UpperCovers>\n");
			// itérateur de tranche CSR : aucune allocation de set par concept
			for (Iterator<Integer> it = order.getUpperCoverIterator(cpt); it.hasNext();) {
				buff.write("<Concept_Ref>");
				buff.write("" + it.next());
				buff.write("</Concept_Ref>\n");
			}
			buff.write("</UpperCovers>\n");
			buff.write("</Concept>\n");
		}
	}
}