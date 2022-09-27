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
