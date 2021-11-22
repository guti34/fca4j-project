package fr.lirmm.fca4j.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import au.com.bytecode.opencsv.CSVReader;
import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;

public class Binarizer extends Command {
	protected File outputFile;
	protected File inputFile;
	protected ContextFormat inputFormat;
	protected ContextFormat outputFormat;
	protected boolean allAttributes = true;
	protected String[] selectedAttributes = new String[0];

	public Binarizer(ISetContext setContext) {
		super("binarize", "transform multivalued data table to formal context",setContext);
	}

	@Override
	void createOptions() {
		options.addOption(
				Option.builder("excl").hasArgs().argName("ATTR_NAME").desc("list attributes to exclude").build());
		options.addOption(
				Option.builder("incl").hasArgs().argName("ATTR_NAME").desc("list attributes to binarize").build());
		// input format
		options.addOption(Option.builder("i")
				.desc("supported formats are:\n* CSV (Comma separated values)\n* [upcoming formats...]").hasArg()
				.argName("INPUT-FORMAT").build());
		// output format
		options.addOption(Option.builder("o")
				.desc("supported formats are:\n* CXT (Burmeisters ConImp)\n* SLF (HTK Standard Latice Format)\n* XML (Galicia v3)\n* CEX (ConExp)\n* CSV (Comma separated values)")
				.hasArg().argName("OUTPUT-FORMAT").build());

		declareCommon();
	}

/*	
	void declareNameGenerator(){		
	// include obj names
	options.addOption(Option.builder("gobj")
			.desc("generate missing objet names (CSV format only)")
			.hasArg().argName("SEPARATOR").build());
	// include attr names
	options.addOption(Option.builder("gattr")
			.desc("generate missing attribute names (CSV format only)")
			.hasArg().argName("SEPARATOR").build());
}
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
		if (inputFormat != ContextFormat.CSV) {
			throw new Exception("only CSV file format is supported");
		}
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
			outputFormat = inputFormat;
		// attribute selection
		if (line.hasOption("excl")&& line.hasOption("incl")) 
			throw new Exception("options -incl and -excl are not compatible");
			if (line.hasOption("excl")) {
				selectedAttributes = line.getOptionValues("excl");
			}
			else if (line.hasOption("incl")) {
				allAttributes=false;
				selectedAttributes = line.getOptionValues("incl");
			} 		
			checkSeparator(line);
		// verbose
		checkVerbose(line);
	}
	protected void checkNameGenerator(CommandLine line) throws Exception {
		generateObjNames=line.hasOption("gobj");
		generateAttrNames=line.hasOption("gattr");
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
		System.out.println(name() + " from " + inputFile.getName() + " to " + outputName);

		CSVReader csvReader = new CSVReader(new FileReader(inputFile), separator);
		List<String[]> records = csvReader.readAll();
		csvReader.close();
		// build selected columns
		TreeMap<Integer, String> selectedColumns = new TreeMap<>();
		for (int column = 1; column < records.get(0).length; column++) {
			if (allAttributes) {

				boolean except = false;
				for (int j = 0; j < selectedAttributes.length; j++) {
					if (selectedAttributes[j].equalsIgnoreCase(records.get(0)[column])) {
						except = true;
						break;
					}
				}
				if (!except)
					selectedColumns.put(column, records.get(0)[column]);
			} else {
				for (int j = 0; j < selectedAttributes.length; j++) {
					if (selectedAttributes[j].equalsIgnoreCase(records.get(0)[column])) {
						selectedColumns.put(column, records.get(0)[column]);
						break;
					}
				}
			}
		}
		// build binarized attributes
		ArrayList<String> objectNames = new ArrayList<>();
		ArrayList<String> attrNames = new ArrayList<>();
		for (int i = 1; i < records.size(); i++) {
			String[] record = records.get(i);
			objectNames.add(record[0]);
			// build normal attributes
			for (Iterator<Integer> it = selectedColumns.keySet().iterator(); it.hasNext();) {

				int column = it.next();
				String attr_name = records.get(0)[column] + "_" + record[column];
				// format the value
				attr_name = formatAttributeName(attr_name);
				int index = attrNames.indexOf(attr_name);
				if (index < 0) {
					attrNames.add(attr_name);
				}
			}
		}
		// create and populate IBinaryContext
		IBinaryContext context = new BinaryContext(objectNames.size(), attrNames.size(), outputName,
				setContext.getDefaultFactory());
		for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
			context.addAttributeName(attrNames.get(numattr));
		}
		for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
			context.addObjectName(objectNames.get(numobj));
		}
		// passe 2
		for (int k = 1; k < records.size(); k++) {
			int numobj = k - 1;
			String[] record = records.get(k);
			for (Iterator<Integer> it = selectedColumns.keySet().iterator(); it.hasNext();) {

				int column = it.next();
				String attr_name = selectedColumns.get(column) + "_" + record[column];
				attr_name = formatAttributeName(attr_name);
				int attrIndex = attrNames.indexOf(attr_name);
				context.set(numobj, attrIndex, true);
			}
		}

		// write result
		writeContext(context, writer, outputFormat);
		System.out.print("done");
		return context;
	}

	public static String formatAttributeName(String attrName) {
		return attrName;
	}
}
