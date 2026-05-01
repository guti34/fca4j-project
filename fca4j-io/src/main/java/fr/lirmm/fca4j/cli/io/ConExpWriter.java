/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fr.lirmm.fca4j.core.IBinaryContext;

/**
 * The Class ConExpWriter.
 *
 * @author agutierr
 */
public class ConExpWriter extends XMLWriter {
	
    /**
     * Write context.
     *
     * @param writer the writer
     * @param context the context
     * @throws Exception the exception
     */
    public static void writeContext(BufferedWriter writer,IBinaryContext context) throws Exception {
        Document doc;
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        doc = builder.newDocument();
        Element root = doc.createElement("ConceptualSystem");
        doc.appendChild(root);
        Element version_elem= addElement(root,"Version");
        version_elem.setAttribute("MajorNumber", "1");
        version_elem.setAttribute("MinorNumber", "0");
        Element contexts_elem = addElement(root, "Contexts");
        Element context_elem = addElement(contexts_elem, "Context");
        context_elem.setAttribute("Type", "Binary");
        context_elem.setAttribute("Identifier", "0");
        Element attributes_elem= addElement(context_elem,"Attributes");
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
            Element attrElement = addElement(attributes_elem, "Attribute");
            attrElement.setAttribute("Identifier",Integer.toString(numattr));
            Element name_element= addElement(attrElement,"Name");
            name_element.setTextContent(context.getAttributeName(numattr));
        }
        Element objects_elem= addElement(context_elem,"Objects");
        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            Element objElement = addElement(objects_elem, "Object");
            Element name_element= addElement(objElement,"Name");
            name_element.setTextContent(context.getObjectName(numobj));
            Element intent_element= addElement(objElement,"Intent");            
            for (Iterator<Integer> it = context.getIntent(numobj).iterator(); it.hasNext();) {
                int numattr = it.next();
                Element hasAttrElement = addElement(intent_element, "HasAttribute");
                hasAttrElement.setAttribute("AttributeIdentifier", Integer.toString(numattr));
            }
        }
        Element lattices_elem = addElement(root, "Lattices");
        writeDocument(doc, writer, "UTF-8");
    }

}
