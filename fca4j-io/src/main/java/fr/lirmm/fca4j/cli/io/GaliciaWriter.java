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
 * The Class GaliciaWriter.
 *
 * @author agutierr
 */
public class GaliciaWriter extends XMLWriter {
    
    /**
     * Write.
     *
     * @param writer the writer
     * @param context the context
     * @throws Exception the exception
     */
    public static void write(BufferedWriter writer,IBinaryContext context) throws Exception {
        Document doc;
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        doc = builder.newDocument();
        Element root = doc.createElement("Galicia_Document");
        doc.appendChild(root);
        Element context_elem = addElement(root, "BinaryContext");
        context_elem.setAttribute("numberObj", Integer.toString(context.getObjectCount()));
        context_elem.setAttribute("numberAtt", Integer.toString(context.getAttributeCount()));
        Element nameElement = addElement(context_elem, "Name");
        nameElement.setTextContent(context.getName());
        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            Element objElement = addElement(context_elem, "Object");
            objElement.setTextContent(context.getObjectName(numobj));
        }
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
            Element attrElement = addElement(context_elem, "Attribute");
            attrElement.setTextContent(context.getAttributeName(numattr));
        }
        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            for (Iterator<Integer> it = context.getIntent(numobj).iterator(); it.hasNext();) {
                int numattr = it.next();
                Element binRelElement = addElement(context_elem, "BinRel");
                Element objElement = addElement(binRelElement, "Object_Ref");
                objElement.setTextContent(context.getObjectName(numobj));
                Element attrElement = addElement(binRelElement, "Attribute_Ref");
                attrElement.setTextContent(context.getAttributeName(numattr));
            }
        }
        writeDocument(doc, writer, "UTF-8");
    }

}
