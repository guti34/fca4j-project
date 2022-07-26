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
