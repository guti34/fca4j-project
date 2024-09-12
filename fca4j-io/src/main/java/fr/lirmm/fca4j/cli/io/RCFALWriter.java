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
import java.io.FileWriter;
import java.io.Writer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.util.AttributeRenamer.MODE;

/**
 * The Class RCFALWriter.
 */
public class RCFALWriter {
    
    /**
     * Write.
     *
     * @param rcf the rcf
     * @param outputPath the output path
     * @throws Exception the exception
     */
    public static void write(RCAFamily rcf,String outputPath,MODE mode) throws Exception{
        File f = new File(outputPath);
        FileWriter fw = new FileWriter(f, false);
        write(rcf,fw,mode);
    }
    public static void write(RCAFamily rcf,String outputPath) throws Exception{
    	write(rcf,outputPath,MODE.SIMPLE);
    }
        
        /**
         * Write.
         *
         * @param rcf the rcf
         * @param writer the writer
         * @throws Exception the exception
         */
    public static void write(RCAFamily rcf,Writer writer) throws Exception{
    	write(rcf,writer,MODE.SIMPLE);
    }
        public static void write(RCAFamily rcf,Writer writer,MODE mode) throws Exception{
         JSONObject json=family2JSON(rcf,mode);
       writer.append(json.toJSONString());
        writer.close();
    }
    
    /**
     * Family 2 JSON.
     *
     * @param rcf the rcf
     * @return the JSON object
     */
    private static JSONObject family2JSON(RCAFamily rcf,MODE mode){
    JSONObject json=new JSONObject();
        json.put("familyName", rcf.getName());
        JSONArray arrayFC=new JSONArray();
        JSONArray arrayRC=new JSONArray();
        json.put("FormalContexts",arrayFC);
        json.put("RelationalContexts",arrayRC);
        for(RCAFamily.FormalContext fc:rcf.getFormalContexts())
        {
            JSONObject fcJson=new JSONObject();
            arrayFC.add(fcJson);
            fcJson.put("name", fc.getName());
            JSONArray objNames=new JSONArray();
            fcJson.put("objects", objNames);
            for(int i=0;i<fc.getContext().getObjectCount();i++)
                objNames.add(fc.getContext().getObjectName(i));
            JSONArray attrNames=new JSONArray();
            fcJson.put("attributes", attrNames);
            for(int i=0;i<fc.getContext().getAttributeCount();i++)
                attrNames.add(fc.getContext().getAttributeName(i));
            JSONArray adjList=new JSONArray();
            fcJson.put("adjList", adjList);
            for(int numobj=0;numobj<fc.getContext().getObjectCount();numobj++)
                for(int numattr=0;numattr<fc.getContext().getAttributeCount();numattr++)
                {
                    if(fc.getContext().get(numobj, numattr)){
                            JSONArray pair=new JSONArray();
                            pair.add(numobj);
                            pair.add(numattr);
                            adjList.add(pair);
                    }
                }
        }
        for(RCAFamily.RelationalContext rc:rcf.getRelationalContexts())
        {
            JSONObject rcJson=new JSONObject();
            arrayRC.add(rcJson);
            rcJson.put("name", rc.getRelationName());
            rcJson.put("source", rcf.getSourceOf(rc).getName());
            rcJson.put("target", rcf.getTargetOf(rc).getName());
            rcJson.put("scaling", rc.getOperator().getName());
            JSONArray objNames=new JSONArray();
            rcJson.put("sourceObjects", objNames);
            for(int i=0;i<rc.getContext().getObjectCount();i++)
                objNames.add(rcf.getSourceOf(rc).getContext().getObjectName(i));
            JSONArray attrNames=new JSONArray();
            rcJson.put("targetObjects", attrNames);
            for(int i=0;i<rc.getContext().getAttributeCount();i++)
                attrNames.add(rcf.getTargetOf(rc).getContext().getObjectName(i));
            JSONArray adjList=new JSONArray();
            rcJson.put("adjList", adjList);
            for(int numobj=0;numobj<rc.getContext().getObjectCount();numobj++)
                for(int numattr=0;numattr<rc.getContext().getAttributeCount();numattr++)
                {
                    if(rc.getContext().get(numobj, numattr)){
                            JSONArray pair=new JSONArray();
                            pair.add(numobj);
                            pair.add(numattr);
                            adjList.add(pair);
                }
        }
    }
        return json;
    }

}
