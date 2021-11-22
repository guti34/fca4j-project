package fr.lirmm.fca4j.cli.io;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.core.RCAFamily;

public class RCFALWriter {
    public static void write(RCAFamily rcf,String outputPath) throws Exception{
        File f = new File(outputPath);
        FileWriter fw = new FileWriter(f, false);
        write(rcf,fw);
    }
        public static void write(RCAFamily rcf,Writer writer) throws Exception{
         JSONObject json=family2JSON(rcf);
       writer.append(json.toJSONString());
        writer.close();
    }
    private static JSONObject family2JSON(RCAFamily rcf){
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
