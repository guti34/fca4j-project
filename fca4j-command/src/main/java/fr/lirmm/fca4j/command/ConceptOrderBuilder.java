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

import fr.lirmm.fca4j.cli.io.GraphVizDotWriter;
import fr.lirmm.fca4j.cli.io.GraphVizDotWriter.DisplayFormat;
import fr.lirmm.fca4j.iset.ISetContext;

public abstract class ConceptOrderBuilder extends Command {

	enum DisplayMode {
		FULL, SIMPLIFIED, MINIMAL
	};
	enum ConceptOrderFormat {
		JSON, XML
	};

	ConceptOrderFormat outputFormat;
	DisplayMode displayMode=DisplayMode.SIMPLIFIED;
	File dotFile;

	ConceptOrderBuilder(String name, String description,ISetContext setContext) {
		super(name, description,setContext);
	}

	protected void declareOutputFormat() {
		// output format
		options.addOption(Option.builder("o").desc("supported formats are:\n* XML (default)\n* JSON\n").hasArg()
				.argName("OUTPUT-FORMAT").build());

	}

	protected void declareGraphvizOptions() {
		// output dot
		options.addOption(
				Option.builder("g").desc(".DOT output file for GraphViz").hasArg().argName("DOTFILE").build());
		options.addOption(Option.builder("d")// .longOpt("format")
				.desc("display format of the concepts for GraphViz. Available formats are:\n* FULL\n* SIMPLIFIED (default)\n* MINIMAL")
				.hasArg().argName("DISPLAY-MODE").build());
	}

	protected void checkDotFile(CommandLine line)  throws Exception{
		if (line.hasOption("g")) {
			dotFile = new File(line.getOptionValue("g"));
			if (!dotFile.exists()) {
				dotFile.createNewFile();
			} else if (!dotFile.canWrite())
				throw new Exception("the specified graphviz file path is not writable !");
			try{
				if(line.hasOption("d"))
					displayMode=DisplayMode.valueOf(line.getOptionValue("d").toUpperCase());
			}catch(Exception e){
				throw new Exception("display mode option not recognized: " + line.getOptionValue("d"));
			}
		} else
			dotFile = null;
	}
	
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
	protected DisplayFormat getDisplayMode() throws Exception
	{
        GraphVizDotWriter.DisplayFormat df;
        switch (displayMode) {
            case FULL:
                df = DisplayFormat.FULL;
                break;
            case MINIMAL:
                df = DisplayFormat.MINIMAL;
                break;
            case SIMPLIFIED:
            default:
                df = DisplayFormat.REDUCED;
        }
        return df;
	}
}
