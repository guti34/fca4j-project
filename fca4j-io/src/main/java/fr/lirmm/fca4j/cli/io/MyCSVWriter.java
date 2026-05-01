/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
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
