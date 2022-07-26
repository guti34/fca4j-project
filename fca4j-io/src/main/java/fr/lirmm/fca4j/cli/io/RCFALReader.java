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

import java.io.FileReader;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

public class RCFALReader {
	    public static RCAFamily read(String filePath)  throws Exception {
        return read(filePath,new BitSetFactory());
    }
    public static RCAFamily read(String filePath,ISetFactory factory) throws Exception {
            JSONParser parser = new JSONParser();
            Object object = parser.parse(new FileReader(filePath));
            JSONObject jsonObject = (JSONObject) object;
            String familyName=(String)jsonObject.get("familyName");
            RCAFamily rcf=new RCAFamily(familyName,factory);	
            JSONArray arrayFC=(JSONArray)jsonObject.get("FormalContexts");
            JSONArray arrayRC=(JSONArray)	jsonObject.get("RelationalContexts");
            for(Object o:arrayFC){
                JSONObject jsonFC=(JSONObject) o;
                addFormalContext(jsonFC, rcf);
            }
            for(Object o:arrayRC){
                JSONObject jsonRC=(JSONObject) o;
                addRelationalContext(jsonRC, rcf);
            }
        return rcf;
    }
    private static void addFormalContext(JSONObject json,RCAFamily rcf){
        String fcName=(String)json.get("name");
        JSONArray attributes=(JSONArray) json.get("attributes");
        JSONArray objects=(JSONArray) json.get("objects");
        BinaryContext context=new BinaryContext(objects.size(), attributes.size(), fcName,rcf.getFactory());
        for (Object n:objects) {
            context.addObjectName((String) n);
        }
        for (Object n:attributes) {
            context.addAttributeName((String) n);
        }
        JSONArray adjList=(JSONArray) json.get("adjList");
        for(Object pair:adjList)
        {
            JSONArray p = (JSONArray) pair;
            int objNum=(int) (long)p.get(0);
            int attrNum=(int) (long)p.get(1);
            context.set(objNum, attrNum, true);
        }
        rcf.addFormalContext(context, null);
    }
    private static void addRelationalContext(JSONObject json,RCAFamily rcf){
        String rcName=(String)json.get("name");
        String source=(String)json.get("source");
        String target=(String)json.get("target");
        String scaling=(String)json.get("scaling");
        JSONArray attributes=(JSONArray) json.get("targetObjects");
        JSONArray objects=(JSONArray) json.get("sourceObjects");
        BinaryContext context=new BinaryContext(objects.size(), attributes.size(), rcName,rcf.getFactory());
        for (Object n:objects) {
            context.addObjectName((String) n);
        }
        for (Object n:attributes) {
            context.addAttributeName((String) n);
        }
        JSONArray adjList=(JSONArray) json.get("adjList");
        for(Object pair:adjList)
        {
            JSONArray p = (JSONArray) pair;
            int objNum=(int) (long)p.get(0);
            int attrNum=(int) (long)p.get(1);
            context.set(objNum, attrNum, true);
        }
        rcf.addRelationalContext(context, source, target, scaling);
        
    }
}
