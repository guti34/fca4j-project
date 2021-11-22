package fr.lirmm.fca4j.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.algo.FastReduction;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetContext;

public class Irreductible extends Command {
	protected File outputFile;
	protected File inputFile;
	private IBinaryContext ctx;
	protected ContextFormat inputFormat;
	protected ContextFormat outputFormat;
	boolean withAttr = false;
	boolean withObj = false;
	boolean unclarified = false;

	public Irreductible(ISetContext setContext) {
		super("irreducible",
				"list irreducible objets (-lobj option) or attributes (-lattr option). Use -u option to operate by class on unclarified context",setContext);
	}

	@Override
	void createOptions() {
		options.addOption(Option.builder("lattr").desc("for attributes").build());
		options.addOption(Option.builder("lobj").desc("for objects").build());
		options.addOption(Option.builder("u")
				.desc("equivalent objects (resp.attributes) are grouped by classes. This option is useful in the case of unclarified contexts")
				.build());
		declareContextFormat("i","INPUT-FORMAT");
		declareImplementation(false);
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
		withAttr = line.hasOption("lattr");
		withObj = line.hasOption("lobj");
		if (!withAttr && !withObj) {
			throw new Exception("option -lobj or -lattr must be specified for reduction");
		}
		if (withAttr && withObj) {
				throw new Exception("options -lobj and -lattr are exclusive");
		}
		unclarified = line.hasOption("u");
		// implementation
		checkImplementation(line);
		checkSeparator(line);
		// verbose
		checkVerbose(line);
	}

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
		System.out.println("list irreducible " + (withAttr ? "attributes" : "objects") + " from " + inputFile.getName()
				+ " to " + outputName);

		ctx=readContext(inputFormat, inputFile);
		if (unclarified) {
			List<ISet> irreductibles;
			if (withAttr)
				irreductibles = FastReduction.computeIrreductibleIntent4notClarifiedContext(ctx);
			else
				irreductibles = FastReduction.computeIrreductibleExtent4notClarifiedContext(ctx);
			if (verbose)
				System.out.println("irreductible: " + irreductibles.size());
			Iterator<ISet> it = irreductibles.iterator();
			while (it.hasNext()) {
				StringBuilder sb = new StringBuilder();
				Iterator<Integer> it2 = it.next().iterator();
				if (withAttr) {
					sb.append(ctx.getAttributeName(it2.next()));
					while (it2.hasNext())
						sb.append(", " + ctx.getAttributeName(it2.next()));
				} else {
					sb.append(ctx.getObjectName(it2.next()));
					while (it2.hasNext())
						sb.append(", " + ctx.getObjectName(it2.next()));

				}
				writer.write(sb.toString());
				writer.newLine();
			}
		} else {
			ISet irreductibles;
			if (withAttr)
				irreductibles = FastReduction.computeIrreductibleIntent(ctx);
			else
				irreductibles = FastReduction.computeIrreductibleExtent(ctx);
			if (verbose)
				System.out.println("irreductible: " + irreductibles.cardinality());
			Iterator<Integer> it = irreductibles.iterator();
			while (it.hasNext()) {
				if (withAttr)
					writer.write(ctx.getAttributeName(it.next()));
				else
					writer.write(ctx.getObjectName(it.next()));
				writer.newLine();
			}

		}
		writer.flush();
		writer.close();
		return ctx;
	}

}
