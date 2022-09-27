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
import java.util.ArrayList;

import au.com.bytecode.opencsv.CSVWriter;
import fr.lirmm.fca4j.core.IBinaryContext;

/**
 * The Class MyCSVWriter.
 *
 * @author agutierr
 */
public class MyCSVWriter {


    /**
     * Write context.
     *
     * @param writer the writer
     * @param context the context
     * @param sep the sep
     * @throws Exception the exception
     */
    public static void writeContext(BufferedWriter writer, IBinaryContext context,char sep) throws Exception {
    	writeContext(writer,context,sep,true,true);
    }
        
        /**
         * Write context.
         *
         * @param writer the writer
         * @param context the context
         * @param sep the sep
         * @param includeAttrNames the include attr names
         * @param includeObjNames the include obj names
         * @throws Exception the exception
         */
        public static void writeContext(BufferedWriter writer, IBinaryContext context,char sep,boolean includeAttrNames,boolean includeObjNames) throws Exception {
    	CSVWriter csvWriter = new CSVWriter(writer, sep, CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);
        if (includeAttrNames) {
            ArrayList<String> attributes = new ArrayList<>();
            if (includeObjNames) {
                attributes.add("");
            }
            for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
                attributes.add(context.getAttributeName(numattr));
            }
            csvWriter.writeNext(attributes.toArray(new String[attributes.size()]));
        }
        for(int numobj=0;numobj<context.getObjectCount();numobj++)
        {
            String[] intent;
            if(includeObjNames) {
                intent=new String[context.getAttributeCount()+1];
                intent[0]=context.getObjectName(numobj);
            }else intent=new String[context.getAttributeCount()];
            for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
                int n=includeObjNames?numattr+1:numattr;
                intent[n]=context.get(numobj,numattr)?"1":"0";
            }                            
            csvWriter.writeNext(intent);
        }
        csvWriter.flush();
        csvWriter.close();
    }
}
