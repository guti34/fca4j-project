package fr.lirmm.fca4j.command;

import java.io.File;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;

public class Inspect extends Command {
	protected File inputFile;
	private IBinaryContext ctx;
	protected ContextFormat inputFormat;

	public Inspect(ISetContext setContext){
		super("inspect", "inspect formal context file and display information about size, density etc.",setContext);
		
	}
	@Override
	void createOptions() {
		declareContextFormat("i","INPUT-FORMAT");	
		declareCommon();
	}

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

	@Override
	public Object exec() throws Exception {
		System.out.println(name()+" file " + inputFile.getName() +"\n");
	ctx = readContext(inputFormat, inputFile);
    System.out.println("objects count: " + ctx.getObjectCount());
    System.out.println("attribute count: " + ctx.getAttributeCount());
    System.out.println("incidence: "+ctx.getIncidence());
    System.out.println("density: " + ctx.getDensity());
    System.out.println("data complexity: " + Math.round(ctx.getDataComplexity()));
    System.out.println("sch�tt number: " + Math.round(ctx.getSchuttNumber()));
	return ctx;	
	}

}
