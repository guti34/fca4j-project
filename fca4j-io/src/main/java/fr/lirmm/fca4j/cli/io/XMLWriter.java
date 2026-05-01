/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The Class XMLWriter.
 *
 * @author agutierr
 */
public class XMLWriter {
    
    /**
     * Adds the element.
     *
     * @param parent the parent
     * @param tagName the tag name
     * @return the element
     */
    protected static Element addElement(Element parent, String tagName) {
        Element elem = parent.getOwnerDocument().createElement(tagName);
        parent.appendChild(elem);
        return elem;
    }

    /**
     * Write document.
     *
     * @param doc the doc
     * @param writer the writer
     * @param encoding the encoding
     * @throws Exception the exception
     */
    protected static void writeDocument(Document doc, Writer writer, String encoding)
            throws Exception {
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
