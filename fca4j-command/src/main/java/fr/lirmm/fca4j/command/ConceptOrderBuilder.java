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
					displayMode=DisplayMode.valueOf(line.getOptionValue("f"));
			}catch(Exception e){}
		} else
			dotFile = null;
	}
	
	protected void checkOutputFormat(CommandLine line, String outFileName) throws Exception {
		if (line.hasOption("o")) {
			try {
				outputFormat = ConceptOrderFormat.valueOf(line.getOptionValue("o"));
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
