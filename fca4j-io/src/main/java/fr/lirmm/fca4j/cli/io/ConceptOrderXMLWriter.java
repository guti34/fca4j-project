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
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


/**
 * The Class ConceptOrderXMLWriter.
 */
public class ConceptOrderXMLWriter {
	
	/**
	 * Write.
	 *
	 * @param buff the buff
	 * @param order the order
	 * @param mbc the mbc
	 * @param reduced the reduced
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
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
	
	/**
	 * Write conceptual structure.
	 *
	 * @param buff the buff
	 * @param order the order
	 * @param mbc the mbc
	 * @param reduced the reduced
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
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
