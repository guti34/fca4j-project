/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
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
