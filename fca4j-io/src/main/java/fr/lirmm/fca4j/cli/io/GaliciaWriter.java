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

import java.io.BufferedWriter;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fr.lirmm.fca4j.core.IBinaryContext;

/**
 *
 * @author agutierr
 */
public class GaliciaWriter extends XMLWriter {
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
