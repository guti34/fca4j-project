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

import fr.lirmm.fca4j.cli.io.RCFALReader;
import fr.lirmm.fca4j.cli.io.RCFTReader;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FRContext;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;
import fr.lirmm.fca4j.iset.ISetContext;

/**
 * The Class FamilyCommand.
 */
public class FamilyCommand extends Command {

	/**
	 * The Enum FamilyAction.
	 */
	enum FamilyAction {
		
	 /** The import action. */
	 IMPORT, 
	 /** The export action. */
	 EXPORT, 
	 /** The remove action. */
	 REMOVE, 
	 /** The rename action. */
	 RENAME
	};

	/** The context file. */
	protected File familyFile, ctxFile;
	
	/** The context file name. */
	protected String ctxName;
	
	/** The new name. */
	protected String newName;
	
	/** The source. */
	protected String source;
	
	/** The target. */
	protected String target;
	
	/** The operator. */
	protected AbstractScalingOperator op;
	
	/** The context format. */
	protected ContextFormat ctxFormat;
	
	/** The family format. */
	protected FamilyFormat familyFormat;
	
	/** The action. */
	protected FamilyAction action;
	
	/** The relation concerned. */
	protected boolean relationConcerned = false;

	/**
	 * Instantiates a new family command.
	 *
	 * @param setContext the set context
	 */
	public FamilyCommand(ISetContext setContext) {
		super("family", "to create and manage relational context families (set of formal contexts and relations)",
				"family", "context",setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		StringBuilder sb_action = new StringBuilder();
		for (FamilyAction action : FamilyAction.values()) {
			sb_action.append("\n* " + action.name());
		}
		// algo
		options.addOption(Option.builder("a").desc("actions are:" + sb_action).hasArg().argName("ACTION").build());
		// name of the context
		options.addOption(
				Option.builder("n").desc("name of the concerned context").hasArg().argName("CONTEXT").build());
		// new name for rename command
		options.addOption(Option.builder("new").desc("new name of the context for rename action").hasArg()
				.argName("CONTEXT").build());
		// operator for rc
		options.addOption(Option.builder("op").desc("scaling operator to import relational context\nsupported operators are:\n* exist,existForall,existContains,equality\n* existForallN,existContainsN (with float parameter as suffix)").hasArg()
				.argName("OPERATOR").build());
		// source and target for rc
		options.addOption(Option.builder("source").desc("source name to import relational context").hasArg()
				.argName("CONTEXT").build());
		options.addOption(Option.builder("target").desc("target name to import relational context").hasArg()
				.argName("CONTEXT").build());

		// family format
		declareFamilyFormat("f", "FAMILY-FORMAT");
		// context format
		declareContextFormat("x", "CONTEXT-FORMAT");
		// common options
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
		// factory
		checkImplementation(line);
		// action
		if (line.hasOption("a")) {
			try {
				action = FamilyAction.valueOf(line.getOptionValue("a"));
			} catch (IllegalArgumentException e) {
			}
			if (action == null)
				throw new Exception("unknown action: " + line.getOptionValue("a"));
		} else
			throw new Exception("use -a option to specify an action");
		// name of the context
		if (!line.hasOption("n"))
			throw new Exception("the name of the context must be specified with -n option");
		ctxName = line.getOptionValue("n");
		if (line.hasOption("new"))
			newName = line.getOptionValue("new");
		else
			newName = null;
		// extra info for relational context
		if (line.hasOption("op") || line.hasOption("source") || line.hasOption("target")) {
			relationConcerned = true;
			if (line.hasOption("op")) {
				String opName = line.getOptionValue("op");
				op=MyScalingOperatorFactory.createScalingOperator(opName);
			} else {
				op = null;
			}
			if (action==action.IMPORT && !line.hasOption("source"))
				throw new Exception("source of relational context to import must be specified with option -source");
			else
				source = line.getOptionValue("source");
			if (action==action.IMPORT && !line.hasOption("target"))
				throw new Exception("target of relational context to import must be specified with option -target");
			else
				target = line.getOptionValue("target");
		}
		// family file
		List<String> args = line.getArgList();
		for (String arg : args) {
			if (name().equalsIgnoreCase(arg)) {
				args.remove(arg);
				break;
			}
		}
		if (args.size() < 1)
			throw new Exception("family file missing");
		String inputFileName = args.get(0);
		familyFile = new File(inputFileName);
		if (!(action == FamilyAction.IMPORT || familyFile.exists()))
			throw new Exception("the specified family file path is not found: " + inputFileName);
		familyFormat = checkFamilyFormat(line, inputFileName, "f");
		if (args.size() > 1) {
			// context file
			String ctxFileName = null;
			ctxFileName = args.get(1);
			ctxFile = new File(ctxFileName);
			// ctxFormat
			ctxFormat = checkContextFormat(line, ctxFileName, "x");
			if (ctxFormat == null)
				throw new Exception("context file format must be specified");

		} else{
			ctxFormat=checkContextFormat(line, null, "x");
			ctxFile = null;
		}
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
		RCAFamily family;
		if (familyFile.exists()) {
			switch (familyFormat) {
			case RCFAL:
				family = RCFALReader.read(familyFile.getPath());
				break;
			case RCFGZ:
				family = RCFTReader.read(familyFile.getPath(), true);
				break;
			case RCFT:
			default:
				family = RCFTReader.read(familyFile.getPath(), false);
				break;

			}
		} else {
			String familyName = familyFile.getName();
			int index = familyName.lastIndexOf(".");
			if (index > 0)
				familyName = familyName.substring(0, index);
			family = new RCAFamily(familyName, factory);
		}
		String msg="do nothing";
		switch (action) {
		case IMPORT: {
			if (ctxFile == null) {
				throw new Exception("context file to import must be specified");
			}
			IBinaryContext ctx = readContext(ctxFormat, ctxFile);
			ctx.setName(ctxName);
			if (relationConcerned) {
				if(family.getFormalContext(source)==null)
					throw new Exception("source "+source+" is not found");
				if(family.getFormalContext(target)==null)
					throw new Exception("target "+target+" is not found");
				if(op==null){
					throw new Exception("scaling operator is missing. Use -op option to specify it");
				}
				family.addRelationalContext(ctx, source, target, op.getName());
			msg="relational context "+ctxName+" is added";
			} else{
				family.addFormalContext(ctx, null);
				msg="formal context "+ctxName+" is added";
			}
			writeFamily(family, familyFile.getPath(), familyFormat,null);
			break;
		}
		case EXPORT: {
			FRContext frc = family.getFormalContext(ctxName);
			if (frc == null && op!=null){
				frc = family.getRelationalContext(ctxName,op.getName());
			}
			if (frc == null)
				throw new Exception("specified context " + ctxName + " is not found. option -op is required if it concern a relational context");
			BufferedWriter writer;
			if (ctxFormat == null)
				throw new Exception("context file format must be specified");
			if (ctxFile != null) {
				writer = new BufferedWriter(new FileWriter(ctxFile));
			} else {
				writer = new BufferedWriter(new OutputStreamWriter(System.out));
			}
			writeContext(frc.getContext(), writer, ctxFormat);
			if (ctxFile != null) msg=ctxFile.getName()+" file is created";
			else msg="export done";
			break;
		}
		case RENAME: {
			if (newName == null) {
				throw new Exception("new name is required to rename context (use -new option)");
			}
			FormalContext fc = family.getFormalContext(ctxName);
			if (fc != null) {
				family.renameFormalContext(fc, newName);
				msg="formal context renamed: "+ctxName+" -> "+newName;
			} else {
				RelationalContext rc=null;
				if(op!=null) rc=family.getRelationalContext(ctxName,op.getName());
				if (rc == null) {
					throw new Exception("specified context " + ctxName + " is not found. option -op is required if it concern a relational context");
				}
				family.renameRelationalContext(rc, newName);
				msg="relational context renamed: "+ctxName+" -> "+newName;
			}
			writeFamily(family, familyFile.getPath(), familyFormat,null);
			break;
		}
		case REMOVE: {
			FormalContext fc = family.getFormalContext(ctxName);
			if (fc != null) {				
				if(!family.deleteFormalContext(fc)){
					throw new Exception("formal context "+ctxName+" cannot be deleted because is impled in a relation. Relation contexts must be deleted before formal contexts");
				}
				msg="formal context "+ctxName+" removed";
			} else {
				RelationalContext rc=null;
				if(op!=null) rc=family.getRelationalContext(ctxName,op.getName());
				if (rc == null) {
					throw new Exception("specified context " + ctxName + " is not found. option -op is required if it concern a relational context");
				}
				if(!family.deleteRelationalContext(rc))
				{
					throw new Exception("relational context "+ctxName+" cannot be deleted");					
				}
				msg="relational context "+ctxName+" removed";
			}
			writeFamily(family, familyFile.getPath(), familyFormat,null);
			break;
		}
		}
		if (verbose) {
			System.out.println(msg);

		}
		return null;
	}

}
