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


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import au.com.bytecode.opencsv.CSVReader;
import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 *
 * @author agutierr
 */
public class MyCSVReader {

    public static IBinaryContext read(File file, char separator, ISetFactory factory) throws IOException {
    	return read(file,separator,true,true,factory);
    }
    public static IBinaryContext read(File file, char separator, boolean inclAttrNames, boolean inclObjNames,ISetFactory factory) throws IOException {
         BufferedReader buff = new BufferedReader(new FileReader(file));
         CSVReader csvReader = new CSVReader(buff, separator);
        List<String[]> lines = csvReader.readAll();
        if (lines.isEmpty() || (lines.size() == 1 && lines.get(0).length < 2)) {
            throw new IOException("Empty file");
        }
        int nb_elem = lines.get(0).length;
        int nb_attr = inclObjNames ? nb_elem - 1 : nb_elem;
        int nb_obj = inclAttrNames ? lines.size() - 1 : lines.size();
        IBinaryContext matrix = new BinaryContext(nb_obj, nb_attr, "Context from CSV",factory);
        if (inclAttrNames) {
            for (int numcol = 0; numcol < nb_elem; numcol++) {
                if (inclObjNames&& numcol == 0) continue;
                matrix.addAttributeName(lines.get(0)[numcol]);
            }
        }
        else{
            for(int numattr=0;numattr<nb_attr;numattr++)
            {
                matrix.addAttributeName("Attr"+numattr);
            }
        }
        String trueValue = null;
        String falseValue = null;

        for (int numline = inclAttrNames ? 1 : 0; numline < lines.size(); numline++) {
            String[] record = lines.get(numline);
            // values representing true and false must be determined
            if (trueValue == null && falseValue == null && record.length>1) {
                String[] values = findValues(record, inclObjNames);
                if (values.length > 2) {
                    throw new IOException("Record error line: " + numline);
                }
                for (String val : values) {
                    if (trueValue == null) {
                        trueValue = detectTrueValue(val);
                    }
                    if (falseValue == null) {
                        falseValue = detectFalseValue(val);
                    }
                }
                if (trueValue == null && falseValue == null ) {
                    throw new IOException("true/false values can't be recognized numline="+numline);
                }
            }
            String objectName;
             if (inclObjNames) {
                objectName = record[0];
            } else {
                objectName = "Object" + numline;
            }
            int numobj=matrix.addObjectName(objectName);
            for (int numcol = 0; numcol < nb_elem; numcol++) {
                int numattr=inclObjNames?numcol-1:numcol;
                if(numattr>=0){
                    if(trueValue!=null)
                        matrix.set(numobj, numattr,record[numcol].trim().equalsIgnoreCase(trueValue));
                    else
                        matrix.set(numobj, numattr,!record[numcol].trim().equalsIgnoreCase(falseValue));
                }
            }
        }
        return matrix;
    }

    private static String[] findValues(String[] record, boolean inclObjName) {
        HashSet<String> values = new HashSet<>();
        for (int numcol = inclObjName ? 1 : 0; numcol < record.length; numcol++) {
            values.add(record[numcol].trim());
        }
        return values.toArray(new String[values.size()]);
    }

    private static String detectTrueValue(String val) {
        switch (val.toLowerCase()) {
            case "x":
            case "1":
            case "true":
            case "vrai":
            case "t":
            case "v":
            case "oui":
            case "yes":
                return val;
        }
        return null;
    }

    private static String detectFalseValue(String val) {
        switch (val.toLowerCase()) {
            case "":
            case "0":
            case "false":
            case "faux":
            case "f":
            case "no":
            case "non":
                return val;
        }
        return null;
    }
}


