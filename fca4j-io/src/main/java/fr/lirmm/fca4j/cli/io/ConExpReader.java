package fr.lirmm.fca4j.cli.io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/**
 *
 * @author agutierr
 */
public class ConExpReader {

    public static List<IBinaryContext> read(File file) throws IOException {
        return read(file,new BitSetFactory());
    }
    public static List<IBinaryContext> read(File file,ISetFactory factory) throws IOException {
        try {
            List<IBinaryContext> results=new ArrayList<>();
            DocumentBuilderFactory DBF = DocumentBuilderFactory.newInstance();
            DocumentBuilder DB = DBF.newDocumentBuilder();
            Document doc = DB.parse(file);
            NodeList nl = doc.getElementsByTagName("ConceptualSystem");
            if (nl == null || nl.getLength() != 1) {
                throw new IOException("Not valid format for CEX file");
            }
            Element root = (Element) nl.item(0);
            nl = root.getElementsByTagName("Contexts");
            if (nl == null || nl.getLength() != 1) {
                throw new IOException("No context to import");
            }
            Element contextsElem = (Element) nl.item(0);
            NodeList context_nl = contextsElem.getElementsByTagName("Context");
            if (context_nl == null || context_nl.getLength() == 0) {
                throw new IOException("No context to import");
            }
            for (int numCtx = 0; numCtx < context_nl.getLength(); numCtx++) {
                Element contextElem = (Element) context_nl.item(numCtx);
                String ctxName = "_" + numCtx;
                if (contextElem.hasAttribute("Identifier")) {
                    ctxName = contextElem.getAttribute("Identifier");
                }
                IBinaryContext matrix = new BinaryContext(0, 0, ctxName,factory);
            HashMap<String, Integer> mapAttrId2Num = new HashMap<>();
                // attributes
                NodeList attrs_nl = contextElem.getElementsByTagName("Attributes");
                if (attrs_nl != null) {
                    Element attributesElem = (Element) attrs_nl.item(0);
                    NodeList attr_nl = attributesElem.getElementsByTagName("Attribute");
                    if (attr_nl != null) {
                        for (int numAttr = 0; numAttr < attr_nl.getLength(); numAttr++) {
                            Element attributeElem = (Element) attr_nl.item(numAttr);
                            if (!attributeElem.hasAttribute("Identifier")) {
                                throw new IOException("Attribute identifier missing");
                            }
                            String attrId = attributeElem.getAttribute("Identifier");
                            mapAttrId2Num.put(attrId, numAttr);
                            NodeList name_nl = attributeElem.getElementsByTagName("Name");
                            if (name_nl == null || name_nl.getLength() != 1) {
                                throw new IOException("Attribute name format error: " + attrId);
                            }
                            Element nameElem = (Element) name_nl.item(0);
                            String attrName = nameElem.getTextContent().trim();
                            matrix.addAttribute(attrName, factory.createSet());
                        }
                    }
                }
                // objects and their intent
                NodeList objects_nl = contextElem.getElementsByTagName("Objects");
                if (objects_nl != null) {
                    Element objectsElem = (Element) objects_nl.item(0);
                    NodeList object_nl = objectsElem.getElementsByTagName("Object");
                    if (object_nl != null) {
                        for (int numObj = 0; numObj < object_nl.getLength(); numObj++) {
                            Element objectElem = (Element) object_nl.item(numObj);
                            // object name
                            NodeList name_nl = objectElem.getElementsByTagName("Name");
                            if (name_nl == null || name_nl.getLength() != 1) {
                                throw new IOException("Object name missing");
                            }
                            Element nameElem = (Element) name_nl.item(0);
                            String objectName = nameElem.getTextContent().trim();
                            // object intent
                            ISet intent = factory.createSet();
                            NodeList intent_nl = objectElem.getElementsByTagName("Intent");
                            if (intent_nl != null) {
                                if (intent_nl.getLength() != 1) {
                                    throw new IOException("Object intent format error: " + objectName);
                                }
                                Element intentElem = (Element) intent_nl.item(0);
                                NodeList hasAttribute_nl = intentElem.getElementsByTagName("HasAttribute");
                                if (hasAttribute_nl != null) {
                                    for (int item = 0; item < hasAttribute_nl.getLength(); item++) {
                                        Element hasAttrElem = (Element) hasAttribute_nl.item(item);
                                        if (!hasAttrElem.hasAttribute("AttributeIdentifier")) {
                                            throw new IOException("Object intent format error: " + objectName);
                                        }
                                        String attrId = hasAttrElem.getAttribute("AttributeIdentifier").trim();
                                        Integer numattr = mapAttrId2Num.get(attrId);
                                        if (numattr == null) {
                                            throw new IOException("Unknown attribute identifier: " + attrId);
                                        }
                                        intent.add(numattr);
                                    }
                                }
                            }
                            matrix.addObject(objectName, intent);
                        }
                    }
                }
                results.add(matrix);
            }
return results;
        } catch (Throwable t) {
            if (t instanceof IOException) {
                throw (IOException) t;
            } else {
                throw new IOException(t);
            }
        }
    }
}
