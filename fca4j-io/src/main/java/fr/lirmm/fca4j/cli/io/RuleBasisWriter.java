package fr.lirmm.fca4j.cli.io;

import java.io.PrintWriter;
import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;

public class RuleBasisWriter {
	/**
	 * Prints the implications.
	 *
	 * @param printWriter  the print writer
	 * @param implications the implications
	 */
	public static void printImplications(PrintWriter printWriter, Iterable<Implication> implications,
			IBinaryContext context) {
		for (Implication implication : implications) {
			int support = implication.getSupportSize();
			printWriter.printf("<%d> %s => %s\n", support, displayAttrs(implication.getPremise(), context),
					displayAttrs(implication.getConclusion(), context));
		}
		printWriter.close();
	}

	public static void printImplications(Iterable<Implication> implications, IBinaryContext context) {
		for (Implication implication : implications) {
			System.out.printf("<%d> %s => %s\n", implication.getSupport().cardinality(),
					displayAttrs(implication.getPremise(), context),
					displayAttrs(implication.getConclusion(), context));
		}

	}
	
	private static String displayAttrs(ISet set, IBinaryContext context) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			if (sb.length() != 0) {
				sb.append(",");
			}
			sb.append(context.getAttributeName(it.next()));
		}
		return sb.toString();
	}
	
}
