package fr.lirmm.fca4j.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.*;

public class ConfigFamilyImport2 {

    private HashMap<String, ParsedFC> formalContexts;
    private HashMap<String, ParsedRC> relationalContexts;

    public static ConfigFamilyImport2 parse(String filePath,boolean xml) throws Exception {
        ConfigFamilyImport2 cfg = new ConfigFamilyImport2();
        if(xml)cfg.parseXml(filePath);
        else cfg.parseJson(filePath);
//        String xmlPath=filePath.replace(".json", ".xml");
//        convertJsonToXml(filePath, xmlPath);
        return cfg;
    }

    private ConfigFamilyImport2() {
        formalContexts = new HashMap<>();
        relationalContexts = new HashMap<>();
    }

    public HashMap<String, ParsedFC> getFormalContexts() {
        return formalContexts;
    }

    public HashMap<String, ParsedRC> getRelationalContexts() {
        return relationalContexts;
    }

    // ---- JSON PARSING (inchangé sauf pour lisibilité)
    private void parseJson(String filePath) throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(new FileReader(filePath));
        parseFormalContextsFromJson((JSONArray) jsonObject.get("FormalContext"));
        if (jsonObject.containsKey("RelationalContext")) {
            parseRelationalContextsFromJson((JSONArray) jsonObject.get("RelationalContext"));
        }
    }

    private void parseFormalContextsFromJson(JSONArray jsonFormalContexts) {
        Iterator<?> iteratorFC = jsonFormalContexts.iterator();
        while (iteratorFC.hasNext()) {
            JSONObject jsonFC = (JSONObject) iteratorFC.next();
            String type = (String) jsonFC.get("nom");
            String path = (String) jsonFC.get("path");
            boolean square = jsonFC.get("square") instanceof Boolean && (Boolean) jsonFC.get("square");
            int[] attrs = parseArrayInteger(jsonFC, "attr");
            int[] attrsBoolean = parseArrayInteger(jsonFC, "attrBoolean");
            int[] ids = parseArrayInteger(jsonFC, "attrID");
            int[] attrsQ = parseArrayInteger(jsonFC, "attrQuartile");
            int[] attrsQBase = parseArrayInteger(jsonFC, "attrQuartileBase");
            int[] attrsQType = parseArrayInteger(jsonFC, "attrQuartileType");
            Map<Integer, Set<int[]>> attrsInterval = parseIntervals(jsonFC, "attrCustomInterval");

            ParsedFC fc = new ParsedFC(type, path, ids, attrs, attrsBoolean, attrsQBase, attrsQ, attrsQType, attrsInterval, square);
            formalContexts.put(type, fc);
        }
    }

    private void parseRelationalContextsFromJson(JSONArray jsonRelationnalContexts) {
        Iterator<?> iteratorRC = jsonRelationnalContexts.iterator();
        while (iteratorRC.hasNext()) {
            JSONObject jsonRC = (JSONObject) iteratorRC.next();
            String relationName = (String) jsonRC.get("nom");
            String quantif = (String) jsonRC.get("quantif");
            String source = (String) jsonRC.get("source");
            String target = (String) jsonRC.get("target");
            String nameInverse = (String) jsonRC.getOrDefault("nomRelationInverse", relationName + "Inversed");
            int[] sourceKeys = parseArrayInteger(jsonRC, "sourceKeys");
            int[] targetKeys = parseArrayInteger(jsonRC, "targetKeys");
            String path = (String) jsonRC.get("path");

            for (int k : sourceKeys) formalContexts.get(source).addKey(k);
            for (int k : targetKeys) formalContexts.get(target).addKey(k);

            ParsedRC rc = new ParsedRC(relationName, nameInverse, quantif, source, target, sourceKeys, targetKeys, path);
            relationalContexts.put(relationName, rc);
        }
    }

    // ---- XML PARSING ----
    private void parseXml(String filePath) throws Exception {
        File xmlFile = new File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList fcNodes = doc.getElementsByTagName("FormalContext");
        for (int i = 0; i < fcNodes.getLength(); i++) {
            Element fcElem = (Element) fcNodes.item(i);
            String name = fcElem.getAttribute("nom");
            String path = fcElem.getAttribute("path");
            boolean square = Boolean.parseBoolean(fcElem.getAttribute("square"));

            int[] ids = parseXmlIntArray(fcElem, "attrID");
            int[] attrs = parseXmlIntArray(fcElem, "attr");
            int[] attrsBool = parseXmlIntArray(fcElem, "attrBoolean");
            int[] attrsQ = parseXmlIntArray(fcElem, "attrQuartile");
            int[] attrsQB = parseXmlIntArray(fcElem, "attrQuartileBase");
            int[] attrsQT = parseXmlIntArray(fcElem, "attrQuartileType");
            Map<Integer, Set<int[]>> attrsInterval = new HashMap<>();

            ParsedFC fc = new ParsedFC(name, path, ids, attrs, attrsBool, attrsQB, attrsQ, attrsQT, attrsInterval, square);
            formalContexts.put(name, fc);
        }

        NodeList rcNodes = doc.getElementsByTagName("RelationalContext");
        for (int i = 0; i < rcNodes.getLength(); i++) {
            Element rcElem = (Element) rcNodes.item(i);
            String name = rcElem.getAttribute("nom");
            String quantif = rcElem.getAttribute("quantif");
            String source = rcElem.getAttribute("source");
            String target = rcElem.getAttribute("target");
            String nameInverse = rcElem.hasAttribute("nomRelationInverse")
                    ? rcElem.getAttribute("nomRelationInverse")
                    : name + "Inversed";
            int[] sourceKeys = parseXmlIntArray(rcElem, "sourceKeys");
            int[] targetKeys = parseXmlIntArray(rcElem, "targetKeys");
            String path = rcElem.getAttribute("path");

            for (int k : sourceKeys) formalContexts.get(source).addKey(k);
            for (int k : targetKeys) formalContexts.get(target).addKey(k);

            ParsedRC rc = new ParsedRC(name, nameInverse, quantif, source, target, sourceKeys, targetKeys, path);
            relationalContexts.put(name, rc);
        }
    }

    private int[] parseXmlIntArray(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() == 0) return new int[0];
        Element el = (Element) list.item(0);
        String[] parts = el.getTextContent().trim().split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    // --- structures identiques ---
    public class ParsedRC {
        public String name, nameInverse, op, source, target, path;
        public int[] sourceKeys, targetKeys;
        public ParsedRC(String name, String nameInverse, String op, String source, String target, int[] sourceKeys, int[] targetKeys, String path) {
            this.name = name; this.nameInverse = nameInverse; this.op = op;
            this.source = source; this.target = target;
            this.sourceKeys = sourceKeys; this.targetKeys = targetKeys; this.path = path;
        }
    }

    public class ParsedFC {
        public String name, path;
        public boolean square;
        public int[] ids, attrs, attrsBoolean, attrsQ, attrsQBase, attrsQType;
        public Map<Integer, Set<int[]>> attrsInterval;
        public HashSet<Integer> keys = new HashSet<>();
        public ParsedFC(String name, String path, int[] ids, int[] attrs, int[] attrsBoolean,
                        int[] attrsQBase, int[] attrsQ, int[] attrsQType,
                        Map<Integer, Set<int[]>> attrsInterval, boolean square) {
            this.name = name; this.path = path;
            this.ids = ids; this.attrs = attrs; this.attrsBoolean = attrsBoolean;
            this.attrsQ = attrsQ; this.attrsQBase = attrsQBase; this.attrsQType = attrsQType;
            this.attrsInterval = attrsInterval; this.square = square;
        }
        private void addKey(int key) { keys.add(key); }
    }

    // --- outils JSON (inchangés) ---
    private int[] parseArrayInteger(JSONObject obj, String arrayName) {
        try {
            JSONArray colId = (JSONArray) obj.get(arrayName);
            int[] ids = new int[colId.size()];
            for (int i = 0; i < colId.size(); i++)
                ids[i] = (int) (long) colId.get(i);
            return ids;
        } catch (Exception e) {
            return new int[0];
        }
    }

    private Map<Integer, Set<int[]>> parseIntervals(JSONObject obj, String arrayName) {
        try {
            JSONArray intervalDescriptors = (JSONArray) obj.get(arrayName);
            HashMap<Integer, Set<int[]>> mapIntervals = new HashMap<>();
            for (Object descriptorObj : intervalDescriptors) {
                JSONObject descriptor = (JSONObject) descriptorObj;
                int attr = (int) (long) descriptor.get("attr");
                HashSet<int[]> setIntervals = new HashSet<>();
                JSONArray intervals = (JSONArray) descriptor.get("intervals");
                for (Object intervalObj : intervals) {
                    JSONObject interval = (JSONObject) intervalObj;
                    int[] bornes = {(int) (long) interval.get("min"), (int) (long) interval.get("max")};
                    setIntervals.add(bornes);
                }
                mapIntervals.put(attr, setIntervals);
            }
            return mapIntervals;
        } catch (Exception e) {
            return null;
        }
    }

    public static void convertJsonToXml(String jsonPath, String xmlOutputPath) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(new FileReader(jsonPath));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement("Config");
        doc.appendChild(root);

        // --- FormalContext ---
        JSONArray formalContexts = (JSONArray) jsonObject.get("FormalContext");
        if (formalContexts != null) {
            for (Object o : formalContexts) {
                JSONObject fc = (JSONObject) o;
                Element fcElem = doc.createElement("FormalContext");
                fcElem.setAttribute("nom", (String) fc.get("nom"));
                fcElem.setAttribute("path", (String) fc.get("path"));
                if (fc.get("square") != null)
                    fcElem.setAttribute("square", fc.get("square").toString());

                addArrayElement(doc, fcElem, "attrID", (JSONArray) fc.get("attrID"));
                addArrayElement(doc, fcElem, "attr", (JSONArray) fc.get("attr"));
                addArrayElement(doc, fcElem, "attrBoolean", (JSONArray) fc.get("attrBoolean"));
                addArrayElement(doc, fcElem, "attrQuartile", (JSONArray) fc.get("attrQuartile"));
                addArrayElement(doc, fcElem, "attrQuartileBase", (JSONArray) fc.get("attrQuartileBase"));
                addArrayElement(doc, fcElem, "attrQuartileType", (JSONArray) fc.get("attrQuartileType"));

                root.appendChild(fcElem);
            }
        }

        // --- RelationalContext ---
        JSONArray relationalContexts = (JSONArray) jsonObject.get("RelationalContext");
        if (relationalContexts != null) {
            for (Object o : relationalContexts) {
                JSONObject rc = (JSONObject) o;
                Element rcElem = doc.createElement("RelationalContext");
                rcElem.setAttribute("nom", (String) rc.get("nom"));
                rcElem.setAttribute("quantif", (String) rc.get("quantif"));
                rcElem.setAttribute("source", (String) rc.get("source"));
                rcElem.setAttribute("target", (String) rc.get("target"));
                if (rc.get("nomRelationInverse") != null)
                    rcElem.setAttribute("nomRelationInverse", (String) rc.get("nomRelationInverse"));
                if (rc.get("path") != null)
                    rcElem.setAttribute("path", (String) rc.get("path"));

                addArrayElement(doc, rcElem, "sourceKeys", (JSONArray) rc.get("sourceKeys"));
                addArrayElement(doc, rcElem, "targetKeys", (JSONArray) rc.get("targetKeys"));

                root.appendChild(rcElem);
            }
        }

        // --- Écriture du XML ---
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(xmlOutputPath)));
    }

    private static void addArrayElement(Document doc, Element parent, String tagName, JSONArray array) {
        if (array == null || array.isEmpty()) return;
        Element el = doc.createElement(tagName);
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = array.iterator();
        while (it.hasNext()) {
            sb.append(it.next().toString());
            if (it.hasNext()) sb.append(",");
        }
        el.setTextContent(sb.toString());
        parent.appendChild(el);
    }

    public static void convertXmlToJson(String xmlPath, String jsonOutputPath) throws Exception {
        File xmlFile = new File(xmlPath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        JSONObject root = new JSONObject();
        JSONArray fcArray = new JSONArray();
        JSONArray rcArray = new JSONArray();

        NodeList fcNodes = doc.getElementsByTagName("FormalContext");
        for (int i = 0; i < fcNodes.getLength(); i++) {
            Element fc = (Element) fcNodes.item(i);
            JSONObject fcJson = new JSONObject();
            fcJson.put("nom", fc.getAttribute("nom"));
            fcJson.put("path", fc.getAttribute("path"));
            fcJson.put("square", fc.getAttribute("square"));
            fcJson.put("attrID", readArrayFromXml(fc, "attrID"));
            fcJson.put("attr", readArrayFromXml(fc, "attr"));
            fcJson.put("attrBoolean", readArrayFromXml(fc, "attrBoolean"));
            fcJson.put("attrQuartile", readArrayFromXml(fc, "attrQuartile"));
            fcJson.put("attrQuartileBase", readArrayFromXml(fc, "attrQuartileBase"));
            fcJson.put("attrQuartileType", readArrayFromXml(fc, "attrQuartileType"));
            fcArray.add(fcJson);
        }

        NodeList rcNodes = doc.getElementsByTagName("RelationalContext");
        for (int i = 0; i < rcNodes.getLength(); i++) {
            Element rc = (Element) rcNodes.item(i);
            JSONObject rcJson = new JSONObject();
            rcJson.put("nom", rc.getAttribute("nom"));
            rcJson.put("quantif", rc.getAttribute("quantif"));
            rcJson.put("source", rc.getAttribute("source"));
            rcJson.put("target", rc.getAttribute("target"));
            rcJson.put("nomRelationInverse", rc.getAttribute("nomRelationInverse"));
            rcJson.put("path", rc.getAttribute("path"));
            rcJson.put("sourceKeys", readArrayFromXml(rc, "sourceKeys"));
            rcJson.put("targetKeys", readArrayFromXml(rc, "targetKeys"));
            rcArray.add(rcJson);
        }

        root.put("FormalContext", fcArray);
        root.put("RelationalContext", rcArray);

        try (FileWriter file = new FileWriter(jsonOutputPath)) {
            file.write(root.toJSONString());
            file.flush();
        }
    }

    private static JSONArray readArrayFromXml(Element parent, String tagName) {
        JSONArray array = new JSONArray();
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            String[] parts = list.item(0).getTextContent().split(",");
            for (String s : parts) {
                if (!s.trim().isEmpty())
                    array.add(Long.parseLong(s.trim()));
            }
        }
        return array;
    }
}
