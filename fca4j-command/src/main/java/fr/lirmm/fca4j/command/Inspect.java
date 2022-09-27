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

import java.io.File;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;

/**
 * The Class Inspect.
 */
public class Inspect extends Command {
	
	/** The input file. */
	protected File inputFile;
	
	/** The context. */
	private IBinaryContext ctx;
	
	/** The input format. */
	protected ContextFormat inputFormat;

	/**
	 * Instantiates a new inspect.
	 *
	 * @param setContext the set context
	 */
	public Inspect(ISetContext setContext){
		super("inspect", "inspect formal context file and display information about size, density etc.",setContext);
		
	}
	
	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		declareContextFormat("i","INPUT-FORMAT");	
		declareCommon();
	}

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
		System.out.println(name()+" file " + inputFile.getName() +"\n");
	ctx = readContext(inputFormat, inputFile);
    System.out.println("objects count: " + ctx.getObjectCount());
    System.out.println("attribute count: " + ctx.getAttributeCount());
    System.out.println("incidence: "+ctx.getIncidence());
    System.out.println("density: " + ctx.getDensity());
    System.out.println("data complexity: " + Math.round(ctx.getDataComplexity()));
    System.out.println("schutt number: " + Math.round(ctx.getSchuttNumber()));
	return ctx;	
	}

}
