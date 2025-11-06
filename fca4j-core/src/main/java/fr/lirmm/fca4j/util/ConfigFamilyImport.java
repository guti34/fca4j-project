package fr.lirmm.fca4j.util;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class ConfigFamilyImport {

    /**
     * La liste des contextes formels et leur nom correspondant
     */
    private HashMap<String, ParsedFC> formalContexts;
    /**
     * La liste des contextes relationnels et leur nom correspondant
     */
    private HashMap<String, ParsedRC> relationalContexts;
    
    public static ConfigFamilyImport parse(String filePath) throws Exception{
    	ConfigFamilyImport cfg=new ConfigFamilyImport();
    	cfg.parseJson(filePath);
    	return cfg;
    }
    
    private ConfigFamilyImport() {
        formalContexts = new HashMap<>();
        relationalContexts = new HashMap<>();    	
    }
    public HashMap<String, ParsedFC> getFormalContexts() {
        return formalContexts;
    }

    public HashMap<String, ParsedRC> getRelationalContexts() {
        return relationalContexts;
    }
    
    private void parseJson(String filePath) throws IOException, ParseException {

        String fileExtension = (filePath.substring(filePath.lastIndexOf(".") + 1));

        if (!fileExtension.equalsIgnoreCase("json")) { //on verifie qu'on essaye bien de parser le bon type de fichier 
            throw new IOException();
        } else {
            JSONParser parser = new JSONParser();
            try {
                Object object = parser.parse(new FileReader(filePath));
                JSONObject jsonObject = (JSONObject) object;

                //recuperation des contextes formels
                JSONArray jsonFormalContexts = (JSONArray) jsonObject.get("FormalContext");
                Iterator iteratorFC = jsonFormalContexts.iterator(); //changement ici
                while (iteratorFC.hasNext()) {
                    //pour chaque contexte formel du fichier, on cree un FormalContext vide rempli uniquement avec le nom et les numeros des colonnes
                    Object jsonFC = iteratorFC.next();
                    if (jsonFC instanceof JSONObject) {
                        String type = (String) ((JSONObject) jsonFC).get("nom");
                        String path = (String) ((JSONObject) jsonFC).get("path");
                        boolean square=false;
                        Object o=((JSONObject) jsonFC).get("square");
                        if(o!=null && o instanceof Boolean)
                            square=(Boolean)o;
                        int[] attrs = parseArrayInteger((JSONObject) jsonFC, "attr");
                        int[] attrsBoolean = parseArrayInteger((JSONObject) jsonFC, "attrBoolean");
                        int[] ids = parseArrayInteger((JSONObject) jsonFC, "attrID");
                        int[] attrsQ = parseArrayInteger((JSONObject) jsonFC, "attrQuartile");
                        int[] attrsQBase = parseArrayInteger((JSONObject) jsonFC, "attrQuartileBase");
                        int[] attrsQType = parseArrayInteger((JSONObject) jsonFC, "attrQuartileType");                        
                        Map<Integer,Set<int[]>> attrsInterval = parseIntervals((JSONObject) jsonFC, "attrCustomInterval");
                        
                        ParsedFC formalContext = new ParsedFC(type, path, ids, attrs, attrsBoolean,attrsQBase,attrsQ, attrsQType,attrsInterval,square);
                        formalContexts.put(type, formalContext);
                    }
                }

                //recuperation des contextes relationnels
                if ((JSONArray) jsonObject.get("RelationalContext") != null) {
                    JSONArray jsonRelationnalContexts = (JSONArray) jsonObject.get("RelationalContext");
                    Iterator iteratorRC = jsonRelationnalContexts.iterator();
                    while (iteratorRC.hasNext()) {
                        //pour chaque contexte relationnel du fichier, on cree un RelationnalContext vide rempli uniquement avec les noms des contextes formels concernes, le nom de la relatione et le quantifieur
                        Object jsonFC = iteratorRC.next();
                        if (jsonFC instanceof JSONObject) {
                            String relationName = (String) ((JSONObject) jsonFC).get("nom");
                            String quantif = (String) ((JSONObject) jsonFC).get("quantif");
                            String source = (String) ((JSONObject) jsonFC).get("source");
                             String target = (String) ((JSONObject) jsonFC).get("target");
                            String nameInverse = (String) ((JSONObject) jsonFC).get("nomRelationInverse");
                            if(nameInverse==null || nameInverse.isEmpty())
                                nameInverse=relationName+"Inversed";
                            
                            int[] sourceKeys = parseArrayInteger((JSONObject) jsonFC, "sourceKeys");
                            int[] targetKeys = parseArrayInteger((JSONObject) jsonFC, "targetKeys");
                            String path=(String)((JSONObject) jsonFC).get("path");
                            // add indexes during build action
                            for(int sourceKey:sourceKeys)
                                formalContexts.get(source).addKey(sourceKey);
                            for(int targetKey:targetKeys)
                                 formalContexts.get(target).addKey(targetKey);
                            ParsedRC relation = new ParsedRC(relationName, nameInverse, quantif, source, target, sourceKeys, targetKeys,path);
                            relationalContexts.put(relationName, relation);
                        }
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    public class ParsedRC {

        public String name;
        public String nameInverse;
        public String op;
        public String source;
        public String target;
        public String path; // path to CSV joigning the both keys. equals to null if keys are included in formal contexts 
        public int[] sourceKeys;
        public int[] targetKeys;

        public ParsedRC(String name, String nameInverse, String op, String source, String target, int[] sourceKeys, int[] targetKeys,String path) {
            this.name = name;
            this.nameInverse = nameInverse;
            this.op = op;
            this.source = source;
            this.target = target;
            this.sourceKeys = sourceKeys;
            this.targetKeys = targetKeys;
            this.path=path;
        }

    }

    public class ParsedFC {
    	public String name;
    	public String path;
    	public boolean square;
    	public int[] ids;
    	public int[] attrs;
    	public int[] attrsBoolean;
    	public int[] attrsQ;
    	public int[] attrsQBase;
    	public int[] attrsQType;
    	public Map<Integer,Set<int[]>> attrsInterval;
    	public HashSet<Integer> keys = new HashSet<>();

        public ParsedFC(String name, String path, int[] ids, int[] attrs, int[] attrsBoolean,int[] attrsQBase,int[] attrsQ, int[]attrsQType, Map<Integer,Set<int[]>> attrsInterval,boolean square) {
            this.name = name;
            this.path = path;
            this.ids = ids;
            this.attrs = attrs;
            this.attrsBoolean=attrsBoolean;
            this.attrsQ = attrsQ;
            this.attrsQBase = attrsQBase;
            this.attrsQType=attrsQType;
            this.attrsInterval=attrsInterval;
            this.square=square;
        }

        private void addKey(int key) {
            keys.add(key);
        }

    }

    private int[] parseArrayInteger(JSONObject obj, String arrayName) {
        try {
        JSONArray colId = (JSONArray) obj.get(arrayName);
        int[] ids = new int[colId.size()];
        int count = 0;
        Iterator it = colId.iterator();
        while (it.hasNext()) {
            Object colonne = it.next();
            ids[count++] = (int) (long) colonne ;
        }
        return ids;
        }catch(Exception e){
            return new int[0];
        }
    }
    private Map<Integer,Set<int[]>> parseIntervals(JSONObject obj, String arrayName) {
        try {
        JSONArray intervalDescriptors = (JSONArray) obj.get(arrayName);
        HashMap<Integer,Set<int[]>> mapIntervals=new HashMap<>();
        Iterator it = intervalDescriptors.iterator();
        while (it.hasNext()) {
             JSONObject descriptor=(JSONObject) it.next();
             int attr=(int) (long)descriptor.get("attr");
             HashSet<int[]> setIntervals=new HashSet<>();
             JSONArray intervals = (JSONArray) descriptor.get("intervals");
             Iterator it2 = intervals.iterator();
             while(it2.hasNext()){
                 JSONObject interval=(JSONObject) it2.next();
                 int[] bornes=new int[2];
                 bornes[0]=(int) (long)interval.get("min");
                 bornes[1]=(int) (long)interval.get("max");
                 setIntervals.add(bornes);
             }
             mapIntervals.put(attr, setIntervals);
        }
             return mapIntervals;
        }catch(Exception e){
            return null;
        }
    }
}
