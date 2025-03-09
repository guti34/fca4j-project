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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.util.AttributeRenamer;
import fr.lirmm.fca4j.util.AttributeRenamer.MODE;
import fr.lirmm.fca4j.util.ConceptOrderFinder;

/**
 * The Class RCFTWriter.
 */
public class RCFTWriter {

	/**
	 * Write.
	 *
	 * @param rcf        the rcf
	 * @param outputPath the output path
	 * @param compressed the compressed
	 * @return the file
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static File write(RCAFamily rcf, String outputPath, boolean compressed,ConceptOrderFinder conceptOrderFinder) throws IOException {
		return write(rcf, outputPath, compressed, MODE.SIMPLE,conceptOrderFinder);
	}

	/**
	 * Write.
	 *
	 * @param rcf        the rcf
	 * @param outputPath the output path
	 * @param compressed the compressed
	 * @param mode       renaming attributes mode
	 * @return the file
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static File write(RCAFamily rcf, String outputPath, boolean compressed, MODE mode,ConceptOrderFinder conceptOrderFinder) throws IOException {
		Writer fw;
		File f = new File(outputPath);
		if (compressed) {
			fw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(f))));
		} else {
			fw = new BufferedWriter(new FileWriter(f, false));
		}
		for (FormalContext fc : rcf.getFormalContexts()) {
			writeFC(fw, rcf, fc.getContext(), mode,conceptOrderFinder);
		}
		for (RelationalContext rc : rcf.getRelationalContexts()) {
			writeRC(fw, rcf, rc.getContext(), rc.getRelationName(), rc.getOperator().getName(),
					rcf.getSourceOf(rc).getName(), rcf.getTargetOf(rc).getName());
		}
		fw.close();
		return f;
	}

	/**
     * Ecrit le contexte formel dans le fichier rcft.
     *
     * @param writer the writer
     * @param context the context
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private static void writeFC(Writer writer, RCAFamily rcf, IBinaryContext context,MODE mode,ConceptOrderFinder conceptOrderFinder) throws IOException {
        //ecriture de l'en-tete du contexte formel
        writer.write("FormalContext " + context.getName() + "\n"
                + "||");

        //ecriture de la premiere ligne du contexte, cad les attributs
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
        	String attrName=context.getAttributeName(numattr);
        	if(mode!=MODE.SIMPLE)
        	{
        		attrName=AttributeRenamer.build(rcf, attrName, mode,-1,conceptOrderFinder);
        	}
            writer.write(attrName + "|");
        }
        writer.write("\n");

        for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
            //remplissage de l'intention de l'objet
            String ret = "|";
            ret += context.getObjectName(numobj) + "|";
            for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
                if (context.get(numobj, numattr)) {
                    ret += "x|";
                } else {
                    ret += "|";
                }
            }
            ret += "\n";
            writer.write(ret);
        }
        writer.write("\n");
    }

	/**
	 * Ecrit le contexte formel dans le fichier rcft.
	 *
	 * @param writer  the writer
	 * @param context the context
	 * @param rname   the rname
	 * @param op      the op
	 * @param source  the source
	 * @param target  the target
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static void writeRC(Writer writer, RCAFamily rcf, IBinaryContext context, String rname, String op,
			String source, String target) throws IOException {
		// ecriture de l'en-tete du contexte relationnel
		writer.write("RelationalContext " + rname + "\n" + "source " + source + "\n" + "target " + target + "\n"
				+ "scaling " + op + "\n" + "||");

		// ecriture de la premiere ligne du contexte, cad les attributs
		for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
			String attrName = context.getAttributeName(numattr);
			writer.write(attrName + "|");
		}
		writer.write("\n");
		StringBuffer buffer = new StringBuffer();
		for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
			// remplissage de l'intention de l'objet
			String ret = "|";
			ret += context.getObjectName(numobj) + "|";
			for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
				if (context.get(numobj, numattr)) {
					ret += "x|";
				} else {
					ret += "|";
				}
			}
			ret += "\n";
			buffer.append(ret);
		}
		writer.write(buffer.toString());
		writer.write("\n");
	}

}
