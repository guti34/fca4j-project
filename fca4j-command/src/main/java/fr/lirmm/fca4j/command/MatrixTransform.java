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
package fr.lirmm.fca4j.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;

/**
 * The Class MatrixTransform.
 */
public abstract class MatrixTransform extends Command {
	
	/** The output file. */
	protected File outputFile;
	
	/** The input file. */
	protected File inputFile;
	
	/** The context. */
	private IBinaryContext ctx;
	
	/** The input format. */
	protected ContextFormat inputFormat;
	
	/** The output format. */
	protected ContextFormat outputFormat;

	/**
	 * Instantiates a new matrix transform.
	 *
	 * @param name the context name
	 * @param desc the description
	 * @param setContext the set context
	 */
	MatrixTransform(String name, String desc,ISetContext setContext) {
		super(name, desc,setContext);
	}

	/**
	 * Gets the context.
	 *
	 * @return the context
	 */
	IBinaryContext getContext() {
		return ctx;
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		declareContextFormat("i","INPUT-FORMAT");
		// output format
		options.addOption(Option.builder("o")
				.desc("supported formats are:\n* CXT (Burmeisters ConImp)\n* SLF (HTK Standard Latice Format)\n* XML (Galicia v3)\n* CEX (ConExp)\n* CSV (Comma separated values)")
				.hasArg().argName("OUTPUT-FORMAT").build());

		declareCommon();
	}

	/**
	 * Transform.
	 *
	 * @return the binary context
	 * @throws Exception the exception
	 */
	abstract protected IBinaryContext transform() throws Exception;

	/**
	 * Check options.
	 *
	 * @param line the command line
	 * @throws Exception the exception
	 */
	@Override
	public void checkOptions(CommandLine line) throws Exception {
		// input file
		List<String> args = line.getArgList();
		for (String arg : args) {
			if (name().equalsIgnoreCase(arg)) {
				args.remove(arg);
				break;
			}
		}
		if (args.size() < 1)
			throw new Exception("input file missing");
		String inputFileName = args.get(0);
		inputFile = new File(inputFileName);
		if (!inputFile.exists())
			throw new Exception("the specified input file path is not found: " + inputFileName);
		inputFormat = checkContextFormat(line, inputFileName, "i");
		// output file
		String outputFileName = null;
		if (args.size() > 1)
			outputFileName = args.get(1);
		if (outputFileName != null) {
			outputFile = new File(outputFileName);
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			} else if (!outputFile.canWrite())
				throw new Exception("the specified output file path for the result is not writable !");
		} else
			outputFile = null;
		// outputFormat
		outputFormat = checkContextFormat(line, outputFileName, "o");
		if (outputFormat == null)
			outputFormat=inputFormat;
		// implementation
		checkImplementation(line);
		checkSeparator(line);
		// verbose
		checkVerbose(line);
	}

	/**
	 * Exec.
	 *
	 * @return the resulting object
	 * @throws Exception the exception
	 */
	@Override
	public Object exec() throws Exception {
		BufferedWriter writer;
		String outputName;
		if (outputFile != null) {
			writer = new BufferedWriter(new FileWriter(outputFile));
			outputName = outputFile.getName();
		} else {
			writer = new BufferedWriter(new OutputStreamWriter(System.out));
			outputName = "standard output stream";
		}
			System.out.println(name()+" from " + inputFile.getName() + " to "
					+ outputName );
		ctx = readContext(inputFormat, inputFile);

		// run transformation
		IBinaryContext transformedBinaryContext = transform();

		// write result
		writeContext(transformedBinaryContext, writer,outputFormat);
			System.out.print("done");
		return ctx;
	}

}
