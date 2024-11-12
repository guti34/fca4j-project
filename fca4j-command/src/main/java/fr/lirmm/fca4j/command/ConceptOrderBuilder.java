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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.util.GraphVizDotWriter;
import fr.lirmm.fca4j.util.GraphVizDotWriter.DisplayFormat;

/**
 * The Class ConceptOrderBuilder.
 */
public abstract class ConceptOrderBuilder extends Command {

	/**
	 * The Enum ConceptOrderFormat.
	 */
	enum ConceptOrderFormat {
		
	/** The json format. */
	JSON, 
	/** The xml format. */
	 XML
	};

	/** The output format. */
	ConceptOrderFormat outputFormat;
	
	/** The display mode. */
	DisplayFormat displayMode=DisplayFormat.SIMPLIFIED;
	
	/** The dot file. */
	File dotFile;

	/**
	 * Instantiates a new concept order builder.
	 *
	 * @param name the name
	 * @param description the description
	 * @param setContext the set context
	 */
	ConceptOrderBuilder(String name, String description,ISetContext setContext) {
		super(name, description,setContext);
	}

	/**
	 * Declare output format.
	 */
	protected void declareOutputFormat() {
		// output format
		options.addOption(Option.builder("o").desc("supported formats are:\n* XML (default)\n* JSON\n").hasArg()
				.argName("OUTPUT-FORMAT").build());

	}

	/**
	 * Declare Graphviz DOT options.
	 */
	protected void declareGraphvizOptions() {
		// output dot
		options.addOption(
				Option.builder("g").desc(".DOT output file for GraphViz").hasArg().argName("DOTFILE").build());
		options.addOption(Option.builder("d")// .longOpt("format")
				.desc("display format of the concepts for GraphViz. Available formats are:\n* FULL\n* SIMPLIFIED (default)\n* MINIMAL")
				.hasArg().argName("DISPLAY-MODE").build());
	}

	/**
	 * Check dot file.
	 *
	 * @param line the command line
	 * @throws Exception the exception
	 */
	protected void checkDotFile(CommandLine line)  throws Exception{
		if (line.hasOption("g")) {
			dotFile = new File(line.getOptionValue("g"));
			if (!dotFile.exists()) {
				dotFile.createNewFile();
			} else if (!dotFile.canWrite())
				throw new Exception("the specified graphviz file path is not writable !");
			try{
				if(line.hasOption("d"))
					displayMode=DisplayFormat.valueOf(line.getOptionValue("d").toUpperCase());
			}catch(Exception e){
				throw new Exception("display mode option not recognized: " + line.getOptionValue("d"));
			}
		} else
			dotFile = null;
	}
	
	/**
	 * Check output format.
	 *
	 * @param line the command line
	 * @param outFileName the output file name
	 * @throws Exception the exception
	 */
	protected void checkOutputFormat(CommandLine line, String outFileName) throws Exception {
		if (line.hasOption("o")) {
			try {
				outputFormat = ConceptOrderFormat.valueOf(line.getOptionValue("o").toUpperCase());
			} catch (Exception e) {
				throw new Exception("output format option not recognized: " + line.getOptionValue("o"));
			}
		} else if (outFileName != null) {
			if (outFileName.toLowerCase().endsWith("json"))
				outputFormat = ConceptOrderFormat.JSON;
			else if (outFileName.toLowerCase().endsWith("xml"))
				outputFormat = ConceptOrderFormat.XML;
			else
				throw new Exception("output format must be specified for file " + outFileName);
		}
	}
	
}
