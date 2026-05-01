/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/**
 * The Class CXTReader.
 */
public class CXTReader {

	   /**
   	 * Read.
   	 *
   	 * @param file the file
   	 * @return the i binary context
   	 * @throws IOException Signals that an I/O exception has occurred.
   	 */
   	public static IBinaryContext read(File file) throws IOException {
	        return read(file, new BitSetFactory());
	    }

	    /**
    	 * Read.
    	 *
    	 * @param file the file
    	 * @param factory the factory
    	 * @return the i binary context
    	 * @throws IOException Signals that an I/O exception has occurred.
    	 */
    	public static IBinaryContext read(File file, ISetFactory factory) throws IOException {
    		BufferedReader buff = new BufferedReader(new FileReader(file));
    		return read(buff,factory);
    	}
	public static IBinaryContext read(BufferedReader buff, ISetFactory factory) throws IOException {
	            try{
	            int nbObj = 0;
	            int nbAtt = 0;
	            // first line must contient B letter correspond to Burmeisters ConImp
	            String line;
	            while ((line = buff.readLine().trim()).isEmpty());
	            if (!line.equalsIgnoreCase("B")) {
	                throw new IOException("File must begin with letter B");
	            }
	            // optional second line contains context name
	            String name = "CXT_Context";
	            line = buff.readLine().trim();
	            try {
	                nbObj = Integer.parseInt(buff.readLine().trim());
	            } catch (NumberFormatException nfe) {
	                if (!line.isEmpty()) {
	                    name = line;
	                }
	                nbObj = Integer.parseInt(buff.readLine().trim());
	            }
	            nbAtt = Integer.parseInt(buff.readLine().trim());
	            IBinaryContext matrix = new BinaryContext(nbObj, nbAtt, name, factory);
	            for (int numobj = 0; numobj < nbObj; numobj++) {
	                do {
	                    line = buff.readLine().trim();
	                } while (numobj == 0 && line.isEmpty());
	                matrix.addObjectName(line);
	            }
	            for (int numattr = 0; numattr < nbAtt; numattr++) {
	                line = buff.readLine().trim();
	                matrix.addAttributeName(line);
	            }
	            for (int numobj = 0; numobj < nbObj; numobj++) {
	                line = buff.readLine().trim();
	                if (line.length() != nbAtt) {
	                    throw new IOException("data does not correspond to number of attribute");
	                }
	                for (int numattr = 0; numattr < nbAtt; numattr++) {
	                    char c = line.charAt(numattr);
	                    if (c == 'X' || c == 'x') {
	                        matrix.set(numobj, numattr, true);
	                    } else if (c != '.') {
	                        throw new IOException("illegal character \'" + c + "\' found");
	                    }
	                }
	            }
	            return matrix;
	            }finally{
	            	buff.close();
	            }
	    }
}
