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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import au.com.bytecode.opencsv.CSVReader;
import fr.lirmm.fca4j.cli.io.CXTReader;
import fr.lirmm.fca4j.cli.io.CXTWriter;
import fr.lirmm.fca4j.cli.io.ConExpReader;
import fr.lirmm.fca4j.cli.io.ConExpWriter;
import fr.lirmm.fca4j.cli.io.GaliciaWriter;
import fr.lirmm.fca4j.cli.io.GaliciaXMLReader;
import fr.lirmm.fca4j.cli.io.MyCSVReader;
import fr.lirmm.fca4j.cli.io.MyCSVWriter;
import fr.lirmm.fca4j.cli.io.RCFALWriter;
import fr.lirmm.fca4j.cli.io.RCFTWriter;
import fr.lirmm.fca4j.cli.io.SLFReader;
import fr.lirmm.fca4j.cli.io.SLFWriter;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.iset.ISetFactory;

public abstract class Command {
	protected Options options = new Options();
	protected final String name;
	protected final String description;
	protected final String argName1;
	protected final String argName2;
	protected ISetContext setContext;
	protected String impl;
	protected ISetFactory factory;
	protected char separator=',';
	protected boolean verbose;
	protected boolean generateAttrNames=false;
	protected boolean generateObjNames=false;

	public enum ContextFormat {
		CXT, SLF, XML, CEX, CSV,
	};
	public enum FamilyFormat {
		RCFT,RCFGZ,RCFAL
	};
	public enum Separator {
		COMMA, SEMICOLON, TAB
	};


	Command(String name, String description,ISetContext setContext) {
		this(name,description,"input","output-file",setContext);
	}
		Command(String name, String description, String argName1, String argName2,ISetContext setContext) {
		this.setContext=setContext;
		this.impl=setContext.getDefaultImplementation();
		this.name = name;
		this.description = description;
		createOptions();
		this.argName1=argName1;
		this.argName2=argName2;
	}

	abstract void createOptions();

	public abstract void checkOptions(CommandLine line) throws Exception;

	public abstract Object exec() throws Exception;

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}
	public String getArgName1(){
		return argName1;
	}
	public String getArgName2(){
		return argName2;
	}
	   private InputStream readExamples() {
	        String path = "/examples.csv";
	        return getClass().getResourceAsStream(path);		   
	   }
	public List<String[]> examples() {
		Reader examplesReader;
		ArrayList<String[]> list=new ArrayList<>();
		try {
			examplesReader = new InputStreamReader(readExamples());
			CSVReader reader=new CSVReader(examplesReader, ';');
			List<String[]> records=reader.readAll();
			boolean first_line=true;
			for(String[] record:records)
			{
				if(first_line)
					first_line=false;
				else{
					if(name.equalsIgnoreCase(record[0]))
						list.add(record);
				}
			}
		} catch (IOException e) {
		}
		return list;
	}
	
	void declareImplementation(boolean sorted) {
		StringBuilder sb_impl = new StringBuilder();
		boolean first = true;
		for (ISetFactory factory : setContext.getImplementations()) {
			if (sorted && !factory.ordered())
				continue;
			sb_impl.append("\n* " + factory.name());
			if (first) {
				sb_impl.append(" (default)");
				first = false;
			}
		}
		// implementation
		options.addOption(Option.builder("m")// .longOpt("implementation")
				.desc("supported implementations are " + sb_impl).hasArg().argName("IMPL").build());

	}
	void declareContextFormat(String key,String name) {
		// input format
		options.addOption(Option.builder(key)
				.desc("supported formats are:\n* CXT (Burmeisters ConImp)\n* SLF (HTK Standard Latice Format)\n* XML (Galicia v3)\n* CEX (ConExp)\n* CSV (Comma separated values)")
				.hasArg().argName(name).build());

	}
	void declareFamilyFormat(String key,String name) {
		// input format
		options.addOption(Option.builder(key)
				.desc("supported formats are:\n* RCFT (default)\n* RCFGZ (compressed RCFT)\n* RCFAL (adjacency list JSON)\n")
				.hasArg().argName(name).build());

	}

	void declareCommon() {
		// separator
		options.addOption(Option.builder("s")
				.desc("separator (CSV format only):\n* COMMA (default)\n* SEMICOLON\n* TAB")
				.hasArg().argName("SEPARATOR").build());
		// timeout
		options.addOption(
				Option.builder("timeout").desc("set timeout for algorithm execution, in seconds")
				.hasArg().argName("SECONDS").build());
		// verbose
		options.addOption(
				Option.builder("v").longOpt("verbose").desc("print a final report of the algorithm execution")
				.build());
	}

	protected IBinaryContext readContext(ContextFormat iformat, File inputFile) throws Exception {
		IBinaryContext context;
		switch (iformat) {
		case CXT:
			context=CXTReader.read(inputFile, factory);
			break;
		case CEX:
			context=ConExpReader.read(inputFile, factory).get(0);
			break;
		case SLF:
			context=SLFReader.read(inputFile, factory);
			break;
		case CSV:
			context=MyCSVReader.read(inputFile, separator, factory);
			break;
		case XML:
			context=GaliciaXMLReader.read(inputFile, factory);
			break;
		default:
			throw new Exception("unknown input file format");
			
		}
		return context;
	}

	protected static ContextFormat suggestContextFormat(String filename) {
		int beginIndex = filename.lastIndexOf('.');
		if (beginIndex >= 0) {
			switch (filename.substring(beginIndex + 1).toUpperCase()) {
			case "CXT":
				return ContextFormat.CXT;
			case "CEX":
				return ContextFormat.CEX;
			case "CSV":
				return ContextFormat.CSV;
			case "SLF":
				return ContextFormat.SLF;
			case "XML":
				return ContextFormat.XML;
			}
		}
		return null;
	}
	protected static FamilyFormat suggestFamilyFormat(String filename) {
		int beginIndex = filename.lastIndexOf('.');
		if (beginIndex >= 0) {
			switch (filename.substring(beginIndex + 1).toUpperCase()) {
			case "RCF":
			case "RCFT":
				return FamilyFormat.RCFT;
			case "RCFGZ":
			case "RCFTGZ":
				return FamilyFormat.RCFGZ;
			case "JSON":
			case "RCFAL":
			case "RCFTAL":
				return FamilyFormat.RCFAL;
			}
		}
		return null;
	}

	protected ContextFormat checkContextFormat(CommandLine line, String fileName,String opt) throws Exception {
		ContextFormat ctxFormat=null;
		if (line.hasOption(opt)) {
			try {
				ctxFormat = ContextFormat.valueOf(line.getOptionValue(opt).toUpperCase());
			} catch (IllegalArgumentException e) {
			}
		}
		if (ctxFormat == null && fileName!=null) {
			ctxFormat = suggestContextFormat(fileName);
		}
		return ctxFormat;
	}
	
	protected FamilyFormat checkFamilyFormat(CommandLine line, String fileName,String opt) throws Exception {
		FamilyFormat ctxFormat=null;
		if (line.hasOption(opt)) {
			try {
				ctxFormat = FamilyFormat.valueOf(line.getOptionValue(opt).toUpperCase());
			} catch (IllegalArgumentException e) {
			}
		}
		if (ctxFormat == null && fileName!=null) {
			ctxFormat = suggestFamilyFormat(fileName);
		}
		return ctxFormat;
	}

	protected void checkImplementation(CommandLine line) throws Exception {
		// implementation
		factory = setContext.getDefaultFactory();
		if (line.hasOption("m")) {
			String impl_str = line.getOptionValue("m");
			try {
				factory = setContext.getFactory(impl_str.toUpperCase());
				impl = factory.name();
			} catch (IllegalArgumentException e) {
			}
		}

	}	
	protected void checkVerbose(CommandLine line) throws Exception {
		verbose=line.hasOption("v");
	}
		protected void checkSeparator(CommandLine line) throws Exception {
	separator = ',';
	if (line.hasOption("s")) {
		switch (Separator.valueOf(line.getOptionValue("s"))) {
		case SEMICOLON:
			separator = ';';
			break;
		case TAB:
			separator = '\t';
			break;
		case COMMA:
		default:
		}
	}
	}
	public Options getOptions() {
		return options;
	}
	public void writeContext(IBinaryContext tContext, BufferedWriter writer,ContextFormat outputFormat) throws Exception {
		// write
		switch (outputFormat) {
		case SLF:
			SLFWriter.writeContext(writer, tContext);
			break;
		case CXT:
			CXTWriter.writeContext(writer, tContext);
			break;
		case CEX:
			ConExpWriter.writeContext(writer, tContext);
			break;
		case XML:
			GaliciaWriter.write(writer, tContext);
			break;
		case CSV:
			MyCSVWriter.writeContext(writer, tContext, separator);
			break;
		default:
			throw new Exception("unknown context output format");
		}

	}
	public void writeFamily(RCAFamily family, String outputPath,FamilyFormat familyFormat) throws Exception {
		// write
		switch (familyFormat) {
		case RCFAL:
			RCFALWriter.write(family, outputPath);
			break;
		case RCFGZ:
			RCFTWriter.write(family, outputPath, true);
			break;
		case RCFT:
			RCFTWriter.write(family, outputPath, false);
			break;
		default:
			throw new Exception("unknown family output format ?");
		}

	}
}
