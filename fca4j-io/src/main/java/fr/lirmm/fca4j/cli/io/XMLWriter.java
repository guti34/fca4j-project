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
 *
 * @author agutierr
 */
public class XMLWriter {
    
    protected static Element addElement(Element parent, String tagName) {
        Element elem = parent.getOwnerDocument().createElement(tagName);
        parent.appendChild(elem);
        return elem;
    }

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
