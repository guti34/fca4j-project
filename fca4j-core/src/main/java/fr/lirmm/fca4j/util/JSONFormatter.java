package fr.lirmm.fca4j.util;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class JSONFormatter {
    private boolean pretty;
    private boolean ordered;
    public JSONFormatter(boolean pretty,boolean ordered)
    {
        this.pretty=pretty;
        this.ordered=ordered;
    }
    public String format(JSONAware json) throws IOException
    {
        if(!(pretty||ordered)) return json.toJSONString(); // nothing to do
        final StringWriter stringWriter = new StringWriter();
            format(json, stringWriter);
        return stringWriter.toString();
        
    }
    public void format(JSONAware jsonA,StringWriter writer) throws IOException
    {
        if(jsonA instanceof JSONArray)
        {
            JSONArray array=(JSONArray)jsonA;
            writer.append('[');
            boolean first=true;
            for(Object elm:array)
            {
                if(first) first=false;
                else writer.append(',');
                writer.append('\n');
                if(elm instanceof JSONAware)
                    format((JSONAware)elm,writer);
                else JSONValue.writeJSONString(elm, writer);
            }
            if(!array.isEmpty()) writer.append('\n');
            writer.append(']');
        }
        else{
            JSONObject json=(JSONObject) jsonA;
            ArrayList<String> keys=new ArrayList<>();
            keys.addAll(json.keySet());
            if(ordered) Collections.sort(keys);
            writer.append('{');
            boolean first=true;
            for(String key:keys)
            {
                if(first) first=false;
                else writer.append(',');
                writer.append('\n');
                Object obj=json.get(key);
                writer.append("\""+key+"\": ");
                if(obj instanceof JSONAware) format((JSONAware)obj,writer);
                else writer.append(JSONValue.toJSONString(obj));
            }
            if(!json.isEmpty()) writer.append('\n');
            writer.append('}');
            
        }
            
    }

}
