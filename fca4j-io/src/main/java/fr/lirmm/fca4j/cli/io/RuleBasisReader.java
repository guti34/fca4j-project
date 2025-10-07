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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;

public class RuleBasisReader {
	/**
	 * Read.
	 *
	 * @param file the file
	 * @return the i binary context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static RuleBasis read(String filePath,IBinaryContext context) throws IOException {
         RuleBasis ruleBasis=new RuleBasis(context);
        // Regular expression to parse a line
        String regex = "<(?<support>\\d+)>\\s*(?<hypothesis>.*?)\\s*=>\\s*(?<conclusion>.*)";
        Pattern pattern = Pattern.compile(regex);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    int supportSize = Integer.parseInt(matcher.group("support"));
                    ISet hypothesis = parseAttributes(matcher.group("hypothesis"),context);
                    ISet conclusion = parseAttributes(matcher.group("conclusion"),context);
                    ruleBasis.addRule(new Implication(hypothesis, conclusion,supportSize));
                } else {
                	throw new IOException("Ignored line (invalid format) : " + line);
                }
            }
        }
        return ruleBasis;
    }

    private static ISet parseAttributes(String attributes,IBinaryContext context)  throws IOException{
        ISet result = context.getFactory().createSet();
        // Expression régulière pour gérer les attributs entre "" ou sans
        String regex = "\"(.*?)\"|([^,\"]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(attributes);

        while (matcher.find()) {
        	String attrName="";
            if (matcher.group(1) != null) {
            	attrName=matcher.group(1).trim(); // Attribut entre ""
             } else if (matcher.group(2) != null) {
            	 attrName=matcher.group(2).trim();// Attribut sans ""
            }
            int numAttr=context.getAttributeIndex(attrName);
            if(numAttr>=0)
            	result.add(numAttr); 
            else {
            	numAttr=Integer.parseInt(attrName);
            	result.add(numAttr);
//            	throw new IOException("Attribute: "+attrName+" not found");
            }
        }
        return result;
    }

}
