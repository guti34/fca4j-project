package fr.lirmm.fca4j.command;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import au.com.bytecode.opencsv.CSVReader;
import fr.lirmm.fca4j.cli.io.RCFALWriter;
import fr.lirmm.fca4j.cli.io.RCFTWriter;
import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.iset.ISetContext;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.util.AttributeRenamer.MODE;
import fr.lirmm.fca4j.util.ConfigFamilyImport;
import fr.lirmm.fca4j.util.ConfigFamilyImport.ParsedFC;
import fr.lirmm.fca4j.util.ConfigFamilyImport.ParsedRC;

public class RCAImport extends Command {

	/** The output file. */
	protected File outputFile;
	/** The output format. */
	protected FamilyFormat outputFormat;

	/** The input file. */
	protected File inputFile;

	private HashMap<String, IBinaryContext> formalContexts = new HashMap<>();
	private HashMap<String, HashMap<Integer, List<String>>> indexes = new HashMap<>();
	private String workingPath;

	/**
	 * Instantiates a new command.
	 *
	 * @param setContext the set context
	 */
	public RCAImport(ISetContext setContext) {
		super("family_import", "transform multivalued data tables to Relational context family file (RCFT)",
				setContext);
	}

	/** The model file. */
	protected File modelFile;

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		// family format
		declareFamilyFormat("f", "FAMILY-FORMAT");
		// common options
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
			throw new Exception("import model file (json) missing");
		String inputFileName = args.get(0);
		inputFile = new File(inputFileName).getCanonicalFile();
		if (!inputFile.exists())
			throw new Exception("the specified model file path is not found: " + inputFileName);
		// output file
		String outputFileName = null;
		if (args.size() > 1)
			outputFileName = args.get(1);
		if (outputFileName != null) {
			outputFile = new File(outputFileName).getCanonicalFile();
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			} else if (!outputFile.canWrite())
				throw new Exception("the specified output file path for the result is not writable !");
		} else
			outputFile = null;
		// outputFormat
		outputFormat = checkFamilyFormat(line, outputFileName, "f");
		checkSeparator(line);
		// verbose
		checkVerbose(line);
	}

	@Override
	public Object exec() throws Exception {
		ConfigFamilyImport config = ConfigFamilyImport.parse(inputFile.getAbsolutePath());
		workingPath = inputFile.getParent();
		RCAFamily family = new RCAFamily("familyName", setContext.getDefaultFactory());
		for (ParsedFC fc : config.getFormalContexts().values()) {
			if(verbose)
				System.out.println("Computing Formal Context " + fc.name + " from " + workingPath + "/" + fc.path);
			IBinaryContext context = buildFormalContext(workingPath + "/" + fc.path, fc.name, fc.ids, fc.keys, fc.attrs,
					fc.attrsBoolean, fc.attrsQBase, fc.attrsQ, fc.attrsQType, fc.attrsInterval, fc.square);
			formalContexts.put(fc.name, context);
			family.addFormalContext(context, null);
		}
		for (ParsedRC rc : config.getRelationalContexts().values()) {
			if(verbose)
				System.out.println("Computing Relational Context " + rc.name);
			IBinaryContext ooContext = buildRelationalContext(rc, config);
			family.addRelationalContext(ooContext, rc.source, rc.target, rc.op);
			/*
			 * if (inverse) { IBinaryContext ooContextInversed = ooContext.transpose();
			 * ooContextInversed.setName(rc.nameInverse);
			 * family.addRelationalContext(ooContextInversed, rc.target, rc.source, rc.op);
			 * }
			 */
		}
		switch (outputFormat) {
		case RCFAL:
			BufferedWriter writer;
			if (outputFile != null)
				writer = new BufferedWriter(new FileWriter(outputFile));
			else
				writer = new BufferedWriter(new OutputStreamWriter(System.out));
			RCFALWriter.write(family, writer, null);
			break;
		case RCFT:
			RCFTWriter.write(family, outputFile == null ? null : outputFile.getCanonicalPath(), false, MODE.SIMPLE,
					null);
			break;
		case RCFGZ:
			RCFTWriter.write(family, outputFile == null ? null : outputFile.getCanonicalPath(), true, MODE.SIMPLE,
					null);
			break;
		}
		return family;
	}

	private IBinaryContext buildRelationalContext(ParsedRC rc, ConfigFamilyImport config) {
		ParsedFC fcSource = config.getFormalContexts().get(rc.source);
		ParsedFC fcTarget = config.getFormalContexts().get(rc.target);
		IBinaryContext ctxSource = formalContexts.get(fcSource.name);
		IBinaryContext ctxTarget = formalContexts.get(fcTarget.name);
		BinaryContext ooContext = new BinaryContext(ctxSource.getObjectCount(), ctxTarget.getObjectCount(), rc.name,
				new BitSetFactory());
		for (int numobj = 0; numobj < ctxSource.getObjectCount(); numobj++) {
			ooContext.addObjectName(ctxSource.getObjectName(numobj));
		}
		for (int numobj = 0; numobj < ctxTarget.getObjectCount(); numobj++) {
			ooContext.addAttributeName(ctxTarget.getObjectName(numobj));
		}
		if (rc.path == null) // works with built indexes
		{
			for (int objSource = 0; objSource < ctxSource.getObjectCount(); objSource++) {
				String valueSource = "";
				for (int i = 0; i < rc.sourceKeys.length; i++) {
					if (i > 0) {
						valueSource += "_";
					}
					valueSource += indexes.get(fcSource.name).get(rc.sourceKeys[i]).get(objSource);
				}
				for (int objTarget = 0; objTarget < ctxTarget.getObjectCount(); objTarget++) {
					String valueTarget = "";
					for (int i = 0; i < rc.targetKeys.length; i++) {
						if (i > 0) {
							valueTarget += "_";
						}
						valueTarget += indexes.get(fcTarget.name).get(rc.targetKeys[i]).get(objTarget);
					}

					ooContext.set(objSource, objTarget,
							!(valueSource.isEmpty() || valueTarget.isEmpty()) && valueSource.equals(valueTarget));
				}
			}
		} else // works with occurences of keys into a CSV file
		{
			int count = 0;
			try {
				String joiningCSVPath = workingPath + "/" + rc.path;
				File csvFile = new File(joiningCSVPath);
				CSVReader csvReader = new CSVReader(new FileReader(csvFile), separator);

				List<String[]> records = csvReader.readAll();
				for (int i = 1; i < records.size(); i++) {
					String[] record = records.get(i);
					String valueSource = formatId(record, rc.sourceKeys);
					String valueTarget = formatId(record, rc.targetKeys);
					int objSource = ctxSource.getObjectIndex(valueSource);
					int objTarget = ctxTarget.getObjectIndex(valueTarget);
					if (objSource >= 0 && objTarget >= 0) {
						count++;
						ooContext.set(objSource, objTarget, true);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ooContext;
	}

	final static List<String> POSITIVE_VALUES = Arrays.asList("1", "x", "X", "true", "True", "TRUE", "T", "t", "vrai",
			"Vrai", "VRAI", "yes", "Yes", "YES", "oui", "Oui", "OUI");

	private IBinaryContext buildFormalContext(String input, String fcName, int[] ids, Set<Integer> keys, int[] attrs,
			int[] attrsBoolean, int[] q_base, int[] quartile, int[] types, Map<Integer, Set<int[]>> attrsInterval,
			boolean square) {
		try {

			ArrayList<Integer> i_list_q = new ArrayList<>();
			ArrayList<Integer> i_list_base_q = new ArrayList<>();
			ArrayList<Integer> d_list_q = new ArrayList<>();
			ArrayList<Integer> d_list_base_q = new ArrayList<>();

			for (int i = 0; i < quartile.length; i++) {
				if (types == null || types.length == 0 || types[i] < 2) {
					i_list_q.add(quartile[i]);
					i_list_base_q.add(q_base[i]);
				} else {
					d_list_q.add(quartile[i]);
					d_list_base_q.add(q_base[i]);
				}
			}
			int[] i_quartiles = convertListToArray(i_list_q);
			int[] i_q_base = convertListToArray(i_list_base_q);
			int[] d_quartiles = convertListToArray(d_list_q);
			int[] d_q_base = convertListToArray(d_list_base_q);
			// ---Ouverture du fichier CSV---
			File csvFile = new File(input);
			CSVReader csvReader = new CSVReader(new FileReader(csvFile), separator);

			List<String[]> records = csvReader.readAll();
			ArrayList<String> objectNames = new ArrayList<>();
			ArrayList<String> attrNames = new ArrayList<>();

			HashMap<Integer, HashMap<String, List<Integer>>> iQuartilesData = new HashMap<>();
			HashMap<Integer, HashMap<String, List<Double>>> dQuartilesData = new HashMap<>();
			HashMap<Integer, HashMap<String, Integer[]>> iQuartiles = new HashMap<>();
			HashMap<Integer, HashMap<String, Double[]>> dQuartiles = new HashMap<>();
			// build key indexes
			HashMap<Integer, List<String>> listByKey = new HashMap<>();
			indexes.put(fcName, listByKey);
			for (int key : keys) {
				listByKey.put(key, new ArrayList<>());
			}
			HashMap<Integer, Integer> numObjects = new HashMap<>();
			// passe 1
			for (int i = 1; i < records.size(); i++) {
				String[] record = records.get(i);
				// build id
				String stringId = formatId(record, ids);
				if (objectNames.contains(stringId)) {
//outputStream.println("doublon: "+stringId);
					continue;
				}
				objectNames.add(stringId);
				numObjects.put(i, objectNames.size() - 1);
				// build key lists
				for (int key : keys) {
					listByKey.get(key).add(record[key]);
				}
				// build normal attributes
				for (int j = 0; j < attrs.length; j++) {
					String attr_name = records.get(0)[attrs[j]] + "_" + record[attrs[j]];
					// format the value
					attr_name = formatAttributeName(attr_name);
					int index = attrNames.indexOf(attr_name);
					if (index < 0) {
						attrNames.add(attr_name);
					}
				}
				// build boolean attributes
				for (int j = 0; j < attrsBoolean.length; j++) {
					String attr_name = records.get(0)[attrsBoolean[j]];
					// format the value
					attr_name = formatAttributeName(attr_name);
					int index = attrNames.indexOf(attr_name);
					if (index < 0) {
						attrNames.add(attr_name);
					}
				}
				// build custom intervals attributes
				if (attrsInterval != null) {
					for (int num_attr : attrsInterval.keySet()) {
						String attr_name;
						Set<int[]> intervals = attrsInterval.get(num_attr);
						for (int[] interval : intervals) {
							attr_name = records.get(0)[num_attr] + "_(" + interval[0] + "-" + interval[1] + ")";
							int attrIndex = attrNames.indexOf(attr_name);
							if (attrIndex < 0) {
								attrNames.add(attr_name);
//								System.out.println("attr added " + attr_name);
							}
						}
					}
				}
				// build integer quartiles attributes
				for (int j = 0; j < i_quartiles.length; j++) {
					int indexBase = i_q_base[j];
					for (int q = 1; q <= 4; q++) {
						String attr_name;
						if (indexBase >= 0) {
							attr_name = records.get(0)[i_quartiles[j]] + "_" + record[indexBase] + "_q" + q;
						} else {
							attr_name = records.get(0)[i_quartiles[j]] + "_q" + q;
						}
						int index = attrNames.indexOf(attr_name);
						if (index < 0) {
							attrNames.add(attr_name);
						}
					}
					HashMap<String, List<Integer>> iValuesByBase = iQuartilesData.get(i_quartiles[j]);
					if (iValuesByBase == null) {
						iValuesByBase = new HashMap<>();
						iQuartilesData.put(i_quartiles[j], iValuesByBase);
					}
					String key = indexBase < 0 ? "" : record[indexBase];
					List<Integer> iValues = iValuesByBase.get(key);
					if (iValues == null) {
						iValues = new ArrayList<>();
						iValuesByBase.put(key, iValues);
					}
					try {
						if (!record[i_quartiles[j]].isEmpty())
							iValues.add(new Integer(record[i_quartiles[j]]));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}
				}
				// build double quartiles attributes
				for (int j = 0; j < d_quartiles.length; j++) {
					int indexBase = d_q_base[j];
					for (int q = 1; q <= 4; q++) {
						String attr_name;
						if (indexBase >= 0) {
							attr_name = records.get(0)[d_quartiles[j]] + "_" + record[indexBase] + "_q" + q;
						} else {
							attr_name = records.get(0)[d_quartiles[j]] + "_q" + q;
						}
						int index = attrNames.indexOf(attr_name);
						if (index < 0) {
							attrNames.add(attr_name);
						}
					}
					HashMap<String, List<Double>> dValuesByBase = dQuartilesData.get(d_quartiles[j]);
					if (dValuesByBase == null) {
						dValuesByBase = new HashMap<>();
						dQuartilesData.put(d_quartiles[j], dValuesByBase);
					}
					String key = indexBase < 0 ? "" : record[indexBase];
					List<Double> dValues = dValuesByBase.get(key);
					if (dValues == null) {
						dValues = new ArrayList<>();
						dValuesByBase.put(key, dValues);
					}
					try {
						if (!record[d_quartiles[j]].isEmpty())
							dValues.add(new Double(record[d_quartiles[j]]));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}
				}
			}
			// calcul des quartiles
			for (int i : dQuartilesData.keySet()) {
				HashMap<String, Double[]> valueByBase = new HashMap<>();
				dQuartiles.put(i, valueByBase);
				for (String key : dQuartilesData.get(i).keySet()) {
					Double[] quartiles = new Double[3];
					quartiles[0] = quartileDouble(dQuartilesData.get(i).get(key), 25.);
					quartiles[1] = quartileDouble(dQuartilesData.get(i).get(key), 50.);
					quartiles[2] = quartileDouble(dQuartilesData.get(i).get(key), 75.);
					valueByBase.put(key, quartiles);
				}
			}
			for (int i : iQuartilesData.keySet()) {
				HashMap<String, Integer[]> valueByBase = new HashMap<>();
				iQuartiles.put(i, valueByBase);
				for (String key : iQuartilesData.get(i).keySet()) {
					List<Integer> l = iQuartilesData.get(i).get(key);
					Integer[] quartiles = new Integer[3];
					quartiles[0] = quartileInteger(iQuartilesData.get(i).get(key), 25.);
					quartiles[1] = quartileInteger(iQuartilesData.get(i).get(key), 50.);
					quartiles[2] = quartileInteger(iQuartilesData.get(i).get(key), 75.);
					valueByBase.put(key, quartiles);
				}
			}
			BinaryContext context = new BinaryContext(objectNames.size(), attrNames.size(), fcName,
					new BitSetFactory());

			for (String attrName : attrNames) {
				context.addAttributeName(attrName);
			}
			for (String objName : objectNames) {
				context.addObjectName(objName);
			}
			// passe 2
			for (int k = 1; k < records.size(); k++) {
				if (numObjects.get(k) == null) {
					continue; // doublon
				}
				int numobj = numObjects.get(k);
				String[] record = records.get(k);
				// les attributs normaux
				for (int numattr : attrs) {
					String attr_name = records.get(0)[numattr] + "_" + record[numattr];
					attr_name = formatAttributeName(attr_name);
					int attrIndex = attrNames.indexOf(attr_name);
					if (attrIndex == -1) {
						System.out.println("attr_name introuvable=" + attr_name);
						System.out.println("liste des attributs:");
						for (String attr : attrNames) {
							System.out.println(attr);
						}
					}
					context.set(numobj, attrIndex, true);
				}
				// les attributs booléen
				for (int numattr : attrsBoolean) {
					String attr_name = records.get(0)[numattr];
					attr_name = formatAttributeName(attr_name);
					int attrIndex = attrNames.indexOf(attr_name);
					if (attrIndex == -1) {
						System.out.println("attr_name introuvable=" + attr_name);
						System.out.println("liste des attributs:");
						for (String attr : attrNames) {
							System.out.println(attr);
						}
					}
					// boolean value
					boolean booleanValue = POSITIVE_VALUES.contains(record[numattr]);
					context.set(numobj, attrIndex, booleanValue);
				}
				// les attributs avec intervalles parametres
				if (attrsInterval != null) {
					for (int num_attr : attrsInterval.keySet()) {
						String attr_name;
						Set<int[]> intervals = attrsInterval.get(num_attr);
						String value = record[num_attr];
						if (!(value.isEmpty() || "#N/A".equals(value) || "N/A".equals(value))) {
							Integer intValue = new Integer(value);
							for (int[] interval : intervals) {
								if (intValue >= interval[0] && intValue <= interval[1]) {
									attr_name = records.get(0)[num_attr] + "_(" + interval[0] + "-" + interval[1] + ")";
									int attrIndex = attrNames.indexOf(attr_name);
									context.set(numobj, attrIndex, true);
								}
							}
						}
					}
				}
				// les attributs avec quartiles
				// integer
				for (int j = 0; j < i_quartiles.length; j++) {
					int indexBase = i_q_base[j];
					HashMap<String, Integer[]> qByValues = iQuartiles.get(i_quartiles[j]);
					String key;
					if (indexBase < 0) {
						key = "";
					} else {
						key = record[indexBase];
					}
					Integer[] quartiles = qByValues.get(key);
					String value = record[i_quartiles[j]];
					try {
						if (!value.isEmpty()) {
							Integer intValue = new Integer(value);
							int q;
							if (intValue < quartiles[0]) {
								q = 1;
							} else if (intValue < quartiles[1]) {
								q = 2;
							} else if (intValue < quartiles[2]) {
								q = 3;
							} else {
								q = 4;
							}
							String attr_name;
							if (indexBase < 0) {
								attr_name = records.get(0)[i_quartiles[j]] + "_q" + q;
							} else {
								attr_name = records.get(0)[i_quartiles[j]] + "_" + key + "_q" + q;
							}
							int attrIndex = attrNames.indexOf(attr_name);
							context.set(numobj, attrIndex, true);
						}
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}
				}
				// double
				for (int j = 0; j < d_quartiles.length; j++) {
					int indexBase = d_q_base[j];
					HashMap<String, Double[]> qByValues = dQuartiles.get(d_quartiles[j]);
					String key;
					if (indexBase < 0) {
						key = "";
					} else {
						key = record[indexBase];
					}
					Double[] quartiles = qByValues.get(key);
					String value = record[d_quartiles[j]];
					try {
						Double dValue = new Double(value);
						int q;
						if (dValue < quartiles[0]) {
							q = 1;
						} else if (dValue < quartiles[1]) {
							q = 2;
						} else if (dValue < quartiles[2]) {
							q = 3;
						} else {
							q = 4;
						}
						String attr_name;
						if (indexBase < 0) {
							attr_name = records.get(0)[d_quartiles[j]] + "_q" + q;
						} else {
							attr_name = records.get(0)[d_quartiles[j]] + "_" + key + "_q" + q;
						}
						int attrIndex = attrNames.indexOf(attr_name);
						if (attrIndex == -1) {
							System.out.println("attr_name introuvable=" + attr_name);
							System.out.println("attributes:");
							for (String attr : attrNames) {
								System.out.println(attr);
							}
						}
						context.set(numobj, attrIndex, true);
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}

				}
			}
			return context;
		} catch (Throwable e) {
			System.out.println("error with formal context " + fcName);
			e.printStackTrace();
		}
		return null;

	}

	public static double quartileDouble(List<Double> values, double lowerPercent) {
		if (values == null || values.size() == 0) {
			throw new IllegalArgumentException("The data array either is null or does not contain any data.");
		}
		// Rank order the values
		double[] v = new double[values.size()];
		for (int i = 0; i < values.size(); i++) {
			v[i] = values.get(i);
		}
		Arrays.sort(v);
		int n = (int) Math.round(v.length * lowerPercent / 100);
		if (n >= v.length)
			n = v.length - 1;
		return v[n];

	}

	public static int quartileInteger(List<Integer> values, double lowerPercent) {

		if (values == null || values.size() == 0) {
			throw new IllegalArgumentException("The data array either is null or does not contain any data.");
		}
		// Rank order the values
		int[] v = new int[values.size()];
		for (int i = 0; i < values.size(); i++) {
			v[i] = values.get(i);
		}
		Arrays.sort(v);

		int n = (int) Math.round(v.length * lowerPercent / 100);
		if (n >= v.length)
			n = v.length - 1;
		return v[n];

	}

	private int[] convertListToArray(List<Integer> listResult) {
		int size = listResult.size();
		int[] result = new int[size];
		int i = 0;
		for (int num : listResult) {
			result[i++] = num;
		}
		return result;
	}

	private String formatId(String[] record, int[] ids) {
		String stringId = "";
		for (int j = 0; j < ids.length; j++) {
			if (j == 0) {
				stringId = record[ids[0]];
			} else {
				stringId += "_" + record[ids[j]];
			}
		}
		return stringId;
	}

	public static String formatAttributeName(String attr_name) {
		attr_name = attr_name.trim();
		if (checkEmpty(attr_name)) {
			return "NotSpecified";
		} else {
			return stripAccents(toCamlCase(attr_name));
		}
	}

	/**
	 * Traitement des cas de cellule vide ou avec une valeur d'erreur du au
	 * dictonnaire
	 *
	 * @param chaine La valeur de la cellule
	 * @return chaine La chaine "Empty"
	 */
	private static boolean checkEmpty(String chaine) {
		switch (chaine.toLowerCase()) {
		case "":
		case "not-specified":
		case "not":
		case "0":
		case "#valeur!":
		case "#value!":
		case "#n/a":
		case "?":
		case "#n/d":
		case "empty":
		case "none":
			return true;
		default:
			return false;
		}
	}

	/**
	 * Passe une chaine de caractere en format CamelCase
	 *
	 * @param chaine La chaine que l'on veut passer en CamelCase
	 * @return chaine La chaine passee en CamelCase
	 */
	private static String toCamlCase(String chaine) {
		String newChaine = "";
		int size = chaine.length();
		char carac;
		// quand on trouve un espace, on passe ce booleen a true pour que le caractere
		// suivant soit une majuscule
		boolean findSpace = true;

		for (int i = 0; i < size; i++) {
			carac = chaine.charAt(i);
			if (carac != ' ' && carac != '×') {
				if (carac == 'œ') { // c'est un caractere special qui fait que : sous window, aucun treillis forme;
									// sous linux, le caractere est remplace par un vilain pour d'interogation
					if (findSpace) {
						findSpace = false;
						newChaine += "Oe";
					} else {
						newChaine += "oe";
					}
				} else {
					if (findSpace) {
						findSpace = false;
						carac = Character.toUpperCase(carac);
					}
					newChaine += carac;
				}
			} else {
				findSpace = true;
			}
		}
		return newChaine;
	}

	/**
	 * Suppression des accents
	 *
	 * @param chaine La chaine que l'on traite
	 * @return chaine La chaine avec les accents supprimes
	 */
	private static String stripAccents(String chaine) {
		chaine = Normalizer.normalize(chaine, Normalizer.Form.NFD);
		chaine = chaine.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		return chaine;
	}

}
