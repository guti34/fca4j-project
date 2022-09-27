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
package fr.lirmm.fca4j.cli.io;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;


/**
 * The Class GaliciaXMLReader.
 */
public class GaliciaXMLReader implements ErrorHandler {

	/**
	 * Read.
	 *
	 * @param file the file
	 * @return the i binary context
	 * @throws Exception the exception
	 */
	public static IBinaryContext read(File file) throws Exception {
		return read(file, new BitSetFactory());
	}

    /**
     * Read.
     *
     * @param iFile the i file
     * @param factory the factory
     * @return the i binary context
     * @throws Exception the exception
     */
    public static IBinaryContext read(File iFile, ISetFactory factory) throws Exception {

        DocumentBuilderFactory DBF = DocumentBuilderFactory.newInstance();
        DocumentBuilder DB = DBF.newDocumentBuilder();
//        DB.setErrorHandler(this);
        
        Document doc = DB.parse(iFile);
        NodeList nl = doc.getElementsByTagName("BinaryContext");
        if (nl == null || nl.getLength() < 1) {
            throw new Exception("Not valid format for Galicia XML Binary Context");
        }
        Element root = (Element) nl.item(0);
        String name = "Context from Galicia";
        nl = root.getElementsByTagName("Name");
        if (nl != null && nl.getLength() > 0) {
            name = nl.item(0).getTextContent().trim();
        }

        int nbObj = Integer.parseInt(root.getAttributes().getNamedItem(
                "numberObj").getNodeValue().trim());
        int nbAtt = Integer.parseInt(root.getAttributes().getNamedItem(
                "numberAtt").getNodeValue().trim());
        IBinaryContext binRel = new BinaryContext(nbObj, nbAtt, name,factory);
        nl = root.getElementsByTagName("Object");
        for (int i = 0; i < nl.getLength(); i++) {
            binRel.addObjectName(nl.item(i).getTextContent().trim());
        }
        nl = root.getElementsByTagName("Attribute");
        for (int i = 0; i < nl.getLength(); i++) {
            binRel.addAttributeName(nl.item(i).getTextContent().trim());
        }
        nl = root.getElementsByTagName("BinRel");
        Element binRelElement;
        for (int i = 0; i < nl.getLength(); i++) {
            binRelElement = (Element) nl.item(i);
            int numobj, numattr;
            if (binRelElement.hasAttribute("idxO")) {
                numobj = Integer.parseInt(binRelElement.getAttribute("idxO"));
                numattr = Integer.parseInt(binRelElement.getAttribute("idxA"));
            } else {
                Element objRef = (Element) binRelElement.getElementsByTagName("Object_Ref").item(0);
                String objectName = objRef.getTextContent();
                Element attrRef = (Element) binRelElement.getElementsByTagName("Attribute_Ref").item(0);
                String attrName = attrRef.getTextContent();
                numobj = binRel.getObjectIndex(objectName);
                numattr = binRel.getAttributeIndex(attrName);
            }
            binRel.set(numobj, numattr, true);
        }
        return binRel;
    }

    /**
     * Warning.
     *
     * @param exception the exception
     * @throws SAXException the SAX exception
     */
    @Override
    public void warning(SAXParseException exception) throws SAXException {
        // mute mode for errors
        throw exception;
    }

    /**
     * Error.
     *
     * @param exception the exception
     * @throws SAXException the SAX exception
     */
    @Override
    public void error(SAXParseException exception) throws SAXException {
        // mute mode for errors
        throw exception;
    }

    /**
     * Fatal error.
     *
     * @param exception the exception
     * @throws SAXException the SAX exception
     */
    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        // mute mode for errors
        throw exception;
    }

}
