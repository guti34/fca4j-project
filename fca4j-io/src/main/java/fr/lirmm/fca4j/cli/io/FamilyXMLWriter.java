/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.Writer;
import java.util.Iterator;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fr.lirmm.fca4j.core.ConceptOrderFamily;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RelationalAttribute;

/**
 * The Class GenerateXml.
 */
public class FamilyXMLWriter {
	private boolean reduced;
	private Document doc = null;
	private RCAFamily rcaFamily;
	private ConceptOrderFamily coFamily;
	private int step;

	/**
	 * Instantiates a new generate xml.
	 *
	 * @param rcaFamily the rca family
	 * @param coFamily  the co family
	 * @param step      the step
	 * @param reduced   the reduced
	 */
	public FamilyXMLWriter(RCAFamily rcaFamily, ConceptOrderFamily coFamily, int step, boolean reduced) {
		this.rcaFamily = rcaFamily;
		this.coFamily = coFamily;
		this.step = step;
		this.reduced = reduced;
	}

	/**
	 * Generate.
	 *
	 * @throws ParserConfigurationException the parser configuration exception
	 */
	public void generate() throws ParserConfigurationException {
		DocumentBuilder builder;
		builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		doc = builder.newDocument();
		Element root = doc.createElement("RCAExplore_Document");
		doc.appendChild(root);
		Element step_elem = addElement(root, "Step");
		step_elem.setAttribute("nb", "" + step);
		for (IConceptOrder co : coFamily.getConceptOrders()) {
			Element latticeElement = addElement(step_elem, "Lattice");
			generateOrderXML(latticeElement, co);
		}

	}

	private void generateOrderXML(Element latticeElement, IConceptOrder co) {
		latticeElement.setAttribute("numberObj", Integer.toString(co.getContext().getObjectCount()));
		latticeElement.setAttribute("numberAtt", Integer.toString(co.getContext().getAttributeCount()));
		int nbCpt = co.getConceptCount();
		latticeElement.setAttribute("numberCpt", Integer.toString(nbCpt));
		Element configElement = addElement(latticeElement, "Config");
		configElement.setAttribute("algo", co.getAlgoName());
		RCAFamily.FormalContext fc = rcaFamily.getFormalContext(co.getContext().getName());
		for (RCAFamily.RelationalContext r : rcaFamily.outgoingRelationalContextOf(fc)) {
//			for (String relation : co..getRelations()){
			Element relationElement = addElement(configElement, "Relation");
			relationElement.setAttribute("name", r.getRelationName());
			Element scalingElement = addElement(relationElement, "Scaling");
			scalingElement.setTextContent(r.getOperator().getName());
		}
		Element nameElement = addElement(latticeElement, "Name");
		nameElement.setTextContent(co.getContext().getName());

		for (int numobj = 0; numobj < co.getContext().getObjectCount(); numobj++) {
			Element objElement = addElement(latticeElement, "Object");
			objElement.setTextContent(fc.getContext().getObjectName(numobj));
		}
		for (int numattr = 0; numattr < co.getContext().getAttributeCount(); numattr++) {
			if (fc.isRelationalAttribute(numattr)) {
				Element relElement = addElement(latticeElement, "RelationalAttribute");
				RelationalAttribute ra = fc.getRelationalAttribute(numattr);
				relElement.setAttribute("relation", ra.getRelationName());
				relElement.setAttribute("scaling", ra.getScaling().getName());
				relElement.setTextContent(ra.getName());
			} else {
				Element relElement = addElement(latticeElement, "Attribute");
				relElement.setTextContent(co.getContext().getAttributeName(numattr));
			}
		}
		for (Iterator<Integer> it_TopDown = co.getTopDownIterator(); it_TopDown.hasNext();) {
			int c = it_TopDown.next();
			Element conceptElement = addElement(latticeElement, "Concept");
			Element idElement = addElement(conceptElement, "ID");
			idElement.setTextContent("" + c);
			Element extentElement = addElement(conceptElement, "Extent");
			Iterator<Integer> it_extent;
			if (reduced) {
				it_extent = co.getConceptReducedExtent(c).iterator();
			} else {
				it_extent = co.getConceptExtent(c).iterator();
			}
			while (it_extent.hasNext()) {
				int numobj = it_extent.next();
				Element objRefElement = addElement(extentElement, "Object_Ref");
				objRefElement.setTextContent(co.getContext().getObjectName(numobj));
			}
			Element intentElement = addElement(conceptElement, "Intent");
			Iterator<Integer> it_intent;
			if (reduced) {
				it_intent = co.getConceptReducedIntent(c).iterator();
			} else {
				it_intent = co.getConceptIntent(c).iterator();
			}
			while (it_intent.hasNext()) {
				int numattr = it_intent.next();
				if (fc.isRelationalAttribute(numattr)) {

					RelationalAttribute ra = fc.getRelationalAttribute(numattr);
					Element raElement = addElement(intentElement, "RelationalAttribute_Ref");
					raElement.setAttribute("relation", ra.getRelationName());
					raElement.setAttribute("scaling", ra.getScaling().getName());
					raElement.setTextContent(ra.getName());
				} else {
					Element raElement = addElement(intentElement, "Attribute_Ref");
					raElement.setTextContent(co.getContext().getAttributeName(numattr));
				}
			}
			Element upperCoversElement = addElement(conceptElement, "UpperCovers");
			for (Iterator<Integer> it = co.getUpperCoverIterator(c); it.hasNext();) {
				Element crElement = addElement(upperCoversElement, "Concept_Ref");
				crElement.setTextContent("" + it.next());
			}
		}
	}

	/**
	 * Adds the element.
	 *
	 * @param parent  the parent
	 * @param tagName the tag name
	 * @return the element
	 */
	protected Element addElement(Element parent, String tagName) {
		Element elem = parent.getOwnerDocument().createElement(tagName);
		parent.appendChild(elem);
		return elem;
	}

	/**
	 * Write document.
	 *
	 * @param doc      the doc
	 * @param writer   the writer
	 * @param encoding the encoding
	 * @throws Exception the exception
	 */
	public void writeDocument(Writer writer, String encoding) throws Exception {
		DOMSource domSource = new DOMSource(doc);
		StreamResult result = new StreamResult(writer);
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		Properties p = transformer.getOutputProperties();
		p.setProperty(OutputKeys.ENCODING, encoding);
		p.setProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperties(p);
		transformer.transform(domSource, result);
		writer.close();
	}

}