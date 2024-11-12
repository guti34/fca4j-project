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


import java.io.BufferedWriter;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;


/**
 * The Class GraphVizDotWriter.
 */
public class GraphVizDotWriter {

	/**
	 * The Enum DisplayFormat.
	 */
	public enum DisplayFormat {/** The reduced. */
REDUCED, /** The full. */
 FULL, /** The minimal. */
 MINIMAL}

	/** The Constant NEW_CONCEPT_COLOR. */
	public final static String NEW_CONCEPT_COLOR = "lightblue";
	
	/** The Constant FUSION_CONCEPT_COLOR. */
	public final static String FUSION_CONCEPT_COLOR = "orange";
	
	/** The lattice. */
	private ConceptOrder lattice;
	
	/** The mbc. */
	private IBinaryContext mbc;
	
	/** The buff. */
	protected BufferedWriter _buff=null;

	/** The display size. */
	private boolean displaySize;
	
	/** The use color. */
	private boolean useColor;
	
	/** The df. */
	private DisplayFormat df;
        
        /** The orientation. */
        private String orientation;
        
        /** The align sibling. */
        private boolean alignSibling;

	/**
	 * Instantiates a new graph viz dot writer.
	 *
	 * @param buff the buff
	 * @param lattice the lattice
	 * @param mbc the mbc
	 * @param df the df
	 * @param displaySize the display size
	 * @param alignSibling the align sibling
	 * @param orientation the orientation
	 */
	public GraphVizDotWriter(BufferedWriter buff, ConceptOrder lattice,DisplayFormat df,boolean displaySize,boolean alignSibling, String orientation) {
		this._buff=buff;
		this.lattice = lattice;
		this.mbc=lattice.getContext();
		this.df=df;
		this.displaySize=displaySize;
		this.useColor = true;
                this.orientation=orientation;
                this.alignSibling=alignSibling;
	}
	
	/**
	 * Generates the dot code corresponding to the concept lattice.
	 *
	 * @return the string
	 */
	protected String buildDot() {
		StringBuffer sb=new StringBuffer();
		appendHeader(sb);

		buildDotConcepts(sb);

		appendFooter(sb);
		return sb.toString();
	}
        
        /**
         * Write concept.
         *
         * @param sb the sb
         * @param concept the concept
         */
        protected void writeConcept(StringBuffer sb,int concept){
			sb.append(""+concept + " ");
                        sb.append("[shape=record,style=filled");
			if ( useColor ) {
			if ( lattice.isNewConcept(concept) )
					sb.append(",fillcolor=" + NEW_CONCEPT_COLOR );
			else if ( lattice.isFusion(concept) )
					sb.append(",fillcolor=" + FUSION_CONCEPT_COLOR );
			}
			
			sb.append(",label=\"{");
			
			sb.append(concept/*.getName()*/);

			if ( displaySize )
			{
				sb.append(" (");
				sb.append("I: " + lattice.getConceptIntent(concept).cardinality());
				sb.append(", ");
				sb.append("E: " + lattice.getConceptExtent(concept).cardinality() );
				sb.append(")");
			}
			sb.append("|");

			switch(df)
			{
			case REDUCED:
				for( Iterator<Integer> it2=lattice.getConceptReducedIntent(concept).iterator();it2.hasNext(); )
					sb.append( mbc.getAttributeName(it2.next()) + "\\n" );
				sb.append("|");
				for( Iterator<Integer> it2=lattice.getConceptReducedExtent(concept).iterator();it2.hasNext(); )
					sb.append( mbc.getObjectName(it2.next()) + "\\n" );
				break;
			case FULL:
				for( Iterator<Integer> it2=lattice.getConceptIntent(concept).iterator();it2.hasNext(); )
					sb.append( mbc.getAttributeName(it2.next()) + "\\n" );
				sb.append("|");
				for( Iterator<Integer> it2=lattice.getConceptExtent(concept).iterator();it2.hasNext(); )
					sb.append( mbc.getObjectName(it2.next()) + "\\n" );
				break;
			case MINIMAL:
				sb.append("Concept ID= "+concept + "\\n" );
				sb.append("|");
				sb.append( "\\n" );
				break;
			}

			sb.append("}\"];\n");
            
        }
	
	/**
	 * Builds the dot concepts.
	 *
	 * @param sb the sb
	 */
	protected void buildDotConcepts(StringBuffer sb) {
		for(Iterator<Integer> it=lattice.getBasicIterator();it.hasNext();)
		 {
			int concept=it.next();
                        writeConcept(sb, concept);
		}
		for(Iterator<Integer> it=lattice.getBasicIterator();it.hasNext();)
		{
			int c=it.next();
                        for(Iterator<Integer> childIterator=lattice.getUpperCoverIterator(c);childIterator.hasNext();){
                            int child=childIterator.next();
				sb.append("\t" + c + " -> " + child+"\n" );
                        }
		}
				
	}


	/**
	 * Append header.
	 *
	 * @param sb the sb
	 */
	private void appendHeader(StringBuffer sb) {
		sb.append("digraph G { \n");
		sb.append("\trankdir="+orientation+";\n");
	}

	/**
	 * Append footer.
	 *
	 * @param sb the sb
	 */
	private void appendFooter(StringBuffer sb) {
		sb.append("}");
	}

	/**
	 * Write.
	 */
	public void write() {
		try {
			String dot=buildDot();
			_buff.write(dot);
			_buff.flush();
			_buff.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	public String getDescription() {
		return "DOT Writer";
	}

}
