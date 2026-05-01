/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
	public static File write(RCAFamily rcf, String outputPath, boolean compressed,
			ConceptOrderFinder conceptOrderFinder) throws IOException {
		return write(rcf, outputPath, compressed, MODE.SIMPLE, conceptOrderFinder);
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
	public static File write(RCAFamily rcf, String outputPath, boolean compressed, MODE mode,
			ConceptOrderFinder conceptOrderFinder) throws IOException {
		Writer fw;
		File f = null;
		if (outputPath == null) {
			if (compressed)
				throw new IOException(
						"Compressed file format cannot be snet to standard output. Please specify target file");
			else {
				fw = new BufferedWriter(new OutputStreamWriter(System.out));
			}
		} else {
			f = new File(outputPath);
			if (compressed) {
				fw = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(f)), "UTF-8"));
			} else {
				fw = new BufferedWriter(new FileWriter(f, false));
			}
		}
		for (FormalContext fc : rcf.getFormalContexts()) {
			writeFC(fw, rcf, fc.getContext(), mode, conceptOrderFinder);
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
	 * @param writer  the writer
	 * @param context the context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static void writeFC(Writer writer, RCAFamily rcf, IBinaryContext context, MODE mode,
			ConceptOrderFinder conceptOrderFinder) throws IOException {
		// ecriture de l'en-tete du contexte formel
		writer.write("FormalContext " + context.getName() + "\n" + "||");

		// ecriture de la premiere ligne du contexte, cad les attributs
		for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
			String attrName = context.getAttributeName(numattr);
			if (mode != MODE.SIMPLE) {
//        		System.out.println("rename from rcft: "+attrName);
				attrName = AttributeRenamer.build(rcf, attrName, mode, -1, conceptOrderFinder);
			}
			writer.write(attrName + "|");
		}
		writer.write("\n");

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
