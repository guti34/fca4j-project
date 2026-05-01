/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;

import fr.lirmm.fca4j.core.IBinaryContext;


/**
 * The Class SLFWriter.
 */
public class SLFWriter {

	/**
	 * Write context.
	 *
	 * @param writer the writer
	 * @param ctx the ctx
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void writeContext(BufferedWriter writer, IBinaryContext ctx) throws IOException {

		writer.write("[Lattice]\n");
		writer.write("" + ctx.getObjectCount() + "\n");
		writer.write("" + ctx.getAttributeCount	() + "\n");
		writer.write("[Objects]\n");
		for (int i = 0; i < ctx.getObjectCount(); i++) {
			writer.write(ctx.getObjectName(i) + "\n");
		}
		writer.write("[Attributes]\n");
		for (int i = 0; i < ctx.getAttributeCount(); i++) {
			writer.write(ctx.getAttributeName(i) + "\n");
		}
		writer.write("[relation]\n");
		for (int i = 0; i < ctx.getObjectCount(); i++) {
			for (int j = 0; j < ctx.getAttributeCount(); j++) {
				writer.write((ctx.get(i, j) ? '1' : '0'));
				writer.write(' ');
			}
			writer.newLine();
		}

		writer.flush();
		writer.close();

	}
}
