/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
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

/**
 * The Class RCFALReader.
 */
public class RCFALReader {
	    
    	/**
    	 * Read.
    	 *
    	 * @param filePath the file path
    	 * @return the RCA family
    	 * @throws Exception the exception
    	 */
    	public static RCAFamily read(String filePath)  throws Exception {
        return read(filePath,new BitSetFactory());
    }
    
    /**
     * Read.
     *
     * @param filePath the file path
     * @param factory the factory
     * @return the RCA family
     * @throws Exception the exception
     */
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
    
    /**
     * Adds the formal context.
     *
     * @param json the json
     * @param rcf the rcf
     */
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
    
    /**
     * Adds the relational context.
     *
     * @param json the json
     * @param rcf the rcf
     */
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
