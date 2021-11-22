package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


public class ConceptOrderXMLWriter {
	public static void write(BufferedWriter buff,ConceptOrder order,IBinaryContext mbc,boolean reduced) throws IOException {
		
		ISet setOfAllObjects=mbc.getFactory().createSet();
		int cpt;
		
		for(Iterator<Integer> it=order.getMaximals().iterator();it.hasNext();){
			cpt=it.next();
			setOfAllObjects.addAll(order.getConceptExtent(cpt));
		}
		
		ISet setOfAllAttributes=mbc.getFactory().createSet();
		for(Iterator<Integer> it=order.getMinimals().iterator();it.hasNext();){
			cpt=it.next();
			setOfAllAttributes.addAll(order.getConceptIntent(cpt));
		}
		
		buff.write("<GSH numberObj=\"" +setOfAllObjects.cardinality()
				+ "\" numberAtt=\"" + setOfAllAttributes.cardinality() 
				+ "\" numberCpt=\"" + order.getConceptCount()+"\">\n");
		
		buff.write("<Name>"+order.getId()+"</Name>\n");
		
		int fo;
		for (Iterator<Integer> it=setOfAllObjects.iterator(); it.hasNext(); ) {
			fo=it.next();
			buff.write("<Object>");
			buff.write(mbc.getObjectName(fo));
			buff.write("</Object>\n");
		}
		buff.flush();

		int fa;
		for (Iterator<Integer> it=setOfAllAttributes.iterator(); it.hasNext(); ) {
			fa=it.next();
			buff.write("<Attribute>");
			buff.write(mbc.getAttributeName(fa));
			buff.write("</Attribute>\n");
		}
		buff.flush();		
		writeConceptualStructure(buff,order,mbc,reduced);
		
		buff.write("</GSH>\n");
		buff.flush();		
				
	}
	static protected void writeConceptualStructure(BufferedWriter buff,ConceptOrder order,IBinaryContext mbc,boolean reduced)throws IOException {
		int fo;
		int fa;
		
		for(Iterator<Integer> it_TopDown=order.getTopDownIterator();it_TopDown.hasNext();){
			
			int cpt=it_TopDown.next();
			
			buff.write("<Concept>\n");
			buff.write("<ID> "+cpt+" </ID>\n");

			buff.write("<Extent>\n");
			Iterator<Integer> it_extent;
			if(reduced) it_extent=order.getConceptReducedExtent(cpt).iterator();
			else it_extent=order.getConceptExtent(cpt).iterator();
			while(it_extent.hasNext()){
				fo=it_extent.next();
				buff.write("<Object_Ref>");
				buff.write(mbc.getObjectName(fo));
				buff.write("</Object_Ref>\n");				
			}
			buff.write("</Extent>\n");
			
			buff.write("<Intent>\n");
			Iterator<Integer> it_intent;
			if(reduced) it_intent=order.getConceptReducedIntent(cpt).iterator();
			else it_intent=order.getConceptIntent(cpt).iterator();
			while(it_intent.hasNext()){
				fa=it_intent.next();
				buff.write("<Attribute_Ref>");
				buff.write(mbc.getAttributeName(fa));
				buff.write("</Attribute_Ref>\n");				
			}
			buff.write("</Intent>\n");
			
			buff.write("<UpperCovers>\n");
			for(Iterator<Integer> it=order.getUpperCover(cpt).iterator();it.hasNext();){
				buff.write("<Concept_Ref>");
				buff.write(""+it.next());
				buff.write("</Concept_Ref>\n");				
			}
			buff.write("</UpperCovers>\n");		
			buff.write("</Concept>\n");								
		}
		
	}

}
