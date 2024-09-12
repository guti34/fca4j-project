package fr.lirmm.fca4j.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;

import fr.lirmm.fca4j.algo.ExploRCA.MyConceptOrderFamily;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.util.AttributeRenamer.MODE;

public class DotBuilder {
	protected final static String LINE_SEPARATOR = System.getProperty("line.separator");

    public static void build(FileWriter buffer2, RCAFamily family,MyConceptOrderFamily conceptOrderFamily,boolean displayConceptNumber,boolean displayIntent,boolean displayExtent,boolean useReducedIntent,boolean useReducedExtent,MODE mode) throws IOException {
		BufferedWriter buffer=new BufferedWriter(buffer2,128000);
        appendHeader(buffer);
        for (ConceptOrder conceptOrder : conceptOrderFamily.getConceptOrders()) {
            appendLine(buffer,"subgraph ", conceptOrder.getContext().getName(), " { ");
            appendLine(buffer,"label=\"", conceptOrder.getContext().getName(), "\";");
		for(Iterator<Integer> itConcept=conceptOrder.getBasicIterator(); itConcept.hasNext(); ) {
                    int c=itConcept.next();
                append(buffer,Integer.toString(c), " ");
                append(buffer,"[shape=record");
                append(buffer,",label=\"{");

                if (displayConceptNumber) {
                    append(buffer,"Concept_" + conceptOrder.getContext().getName() + "_" + c, "|");
                }

                if (displayIntent) {
                    Iterator<Integer> it;
                    if (useReducedIntent) {
                        it = conceptOrder.getConceptReducedIntent(c).iterator();
                    } else {
                        it = conceptOrder.getConceptIntent(c).iterator();
                    }
                    while (it.hasNext()) {
                    	String attrName=conceptOrder.getContext().getAttributeName(it.next());
                    if(mode!=MODE.SIMPLE) {
                    		attrName=AttributeRenamer.build(family,attrName,mode,c);
                    }
                    append(buffer,attrName, "\\n");
                    }
                }

                if (displayExtent) {

                    Iterator<Integer> it;
                    if (useReducedExtent) {
                        it = conceptOrder.getConceptReducedExtent(c).iterator();
                    } else {
                        it = conceptOrder.getConceptExtent(c).iterator();
                    }
                    append(buffer,"|");
                    while (it.hasNext()) {
                        append(buffer,conceptOrder.getContext().getObjectName(it.next()), "\\n");
                    }
                }
                append(buffer,"}\"");
                appendLine(buffer,"];");
            }

		for(Iterator<Integer> itConcept=conceptOrder.getBasicIterator(); itConcept.hasNext(); ) {
                    int c=itConcept.next();
                Iterator<Integer> itc = conceptOrder.getLowerCoverIterator(c);
                while (itc.hasNext()) {
                    appendLine(buffer,"\t", Integer.toString(itc.next()), " -> ", "" + c);
                }
            }
            appendLine(buffer,"}");
        }
        
        appendFooter(buffer);
        buffer.flush();
     }
	
    private static void appendHeader(BufferedWriter buffer) throws IOException {
        appendLine(buffer,"digraph G { ");
        appendLine(buffer,"\trankdir=BT;");
    }

    private static void appendFooter(BufferedWriter buffer) throws IOException {
        append(buffer,"}");
    }
	
	/**
     * Appends the given string to the internal buffer.
     *
     * @param cs the cs
     * @throws IOException Signals that an I/O exception has occurred.
     */
	protected static void append(BufferedWriter buffer,CharSequence... cs) throws IOException {
		for (CharSequence s:cs)
			buffer.append(s);
	}
	
	/**
     * Appends the given string to a dedicated line in the internal buffer.
     *
     * @param s a string.
     * @throws IOException Signals that an I/O exception has occurred.
     */
	protected static void appendLine(BufferedWriter buffer,CharSequence... s) throws IOException {
		append(buffer,s);
		newLine(buffer);

	}
	
	/**
     * Appends an empty line in the internal buffer.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
	protected static void newLine(BufferedWriter buffer) throws IOException {
		append(buffer,LINE_SEPARATOR);
	}
}
