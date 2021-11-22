package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;


public class GraphVizDotWriter {

	public enum DisplayFormat {REDUCED, FULL, MINIMAL}

	public final static String NEW_CONCEPT_COLOR = "lightblue";
	
	public final static String FUSION_CONCEPT_COLOR = "orange";
	
	private ConceptOrder lattice;
	
	private IBinaryContext mbc;
	
	protected BufferedWriter _buff=null;

	private boolean displaySize;
	private boolean useColor;
	private DisplayFormat df;
        private String orientation;
        private boolean alignSibling;

	public GraphVizDotWriter(BufferedWriter buff, ConceptOrder lattice,IBinaryContext mbc,DisplayFormat df,boolean displaySize,boolean alignSibling, String orientation) {
		this._buff=buff;
		this.lattice = lattice;
		this.mbc=mbc;
		this.df=df;
		this.displaySize=displaySize;
		this.useColor = true;
                this.orientation=orientation;
                this.alignSibling=alignSibling;
	}
	/**
	 * Generates the dot code corresponding to the concept lattice.
	 */
	protected String buildDot() {
		StringBuffer sb=new StringBuffer();
		appendHeader(sb);

		buildDotConcepts(sb);

		appendFooter(sb);
		return sb.toString();
	}
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


	private void appendHeader(StringBuffer sb) {
		sb.append("digraph G { \n");
		sb.append("\trankdir="+orientation+";\n");
	}

	private void appendFooter(StringBuffer sb) {
		sb.append("}");
	}

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

	public String getDescription() {
		return "DOT Writer";
	}

}
