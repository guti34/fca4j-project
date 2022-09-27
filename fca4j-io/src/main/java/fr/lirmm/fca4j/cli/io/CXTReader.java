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
	            String name = "Context from CXT";
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
