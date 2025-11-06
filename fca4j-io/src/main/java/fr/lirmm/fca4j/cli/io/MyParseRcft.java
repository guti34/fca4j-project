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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyExistentialScaling;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;


/**
 * A parser for the RCFT relational context family description format.
 * @author Jean-Remy Falleri
 *
 */
public class MyParseRcft {
	
	/**
	 * The Class Pair.
	 */
	private class Pair{
		
		/** The e. */
		public int e; // entity
		
		/** The a. */
		public int a; // attribute
		
		/**
		 * Instantiates a new pair.
		 *
		 * @param e the e
		 * @param a the a
		 */
		public Pair(int e, int a){
			this.e=e;
			this.a=a;
		}
		
	}
	
	/** The line number. */
	private int lineNumber;
	
	/**
	 * Parses the.
	 *
	 * @param path the path
	 * @return the RCA family
	 * @throws Exception the exception
	 */
	public RCAFamily parse(String path) throws Exception{
            return parse(new FileReader(path),new BitSetFactory());
        }
	
	/**
	 * Parses the.
	 *
	 * @param reader the reader
	 * @param factory the factory
	 * @return the RCA family
	 * @throws Exception the exception
	 */
	public RCAFamily parse(Reader reader,ISetFactory factory) throws Exception{
		lineNumber=0;
		RCAFamily rcf = new RCAFamily(reader.toString(),factory);
		BufferedReader input = new BufferedReader(reader);


		String line = input.readLine();
		lineNumber++;
		while( line != null ) {
			String tline = line.trim();
			if ( tline.startsWith("FormalContext") )
				parseOAContext(rcf,input,tline,factory);
			else if ( tline.startsWith("RelationalContext") )
				parseRelationalContext(rcf,input,tline,factory);
			else if (!tline.startsWith("#")&&!tline.equals(""))
				throw new MyParserException("Unknown context type.",lineNumber,0);
			
			line = input.readLine();
			lineNumber++;
		}
                return rcf;
	}

	/**
	 * Parses the OA context.
	 *
	 * @param rcf the rcf
	 * @param input the input
	 * @param desc the desc
	 * @param factory the factory
	 * @throws Exception the exception
	 */
	private void parseOAContext(RCAFamily rcf,BufferedReader input,String desc,ISetFactory factory) throws Exception {

		int oAContextLine=lineNumber;
		String oacName = desc.split("\\ ")[1]; 
		String algoName=null;
        String description=null;
        int algoParam=0;
		
System.out.println("oacName="+oacName);		
		
		input.mark(0);
		String line = input.readLine();
		String trimmedLine = line.trim();
		
		lineNumber++;
		//parse parameters
		
		while (!trimmedLine.startsWith("|"))
		{
		
			if (trimmedLine.split("\\ ").length==0)
				throw new MyParserException("lines after a formal context declaration should declare parameters",lineNumber,0);
			String parameterName=trimmedLine.split("\\ ")[0];
			if (parameterName.equals("algo"))
			{
				if (trimmedLine.split("\\ ").length>3)
					throw new MyParserException("Too many values for algo parameter",lineNumber,0);
				algoName=trimmedLine.split("\\ ")[1];							
					if(trimmedLine.split("\\ ").length==3) {
						try {
                                                    algoParam= Integer.parseInt(trimmedLine.split("\\ ")[2]);
						}
						catch (NumberFormatException e){
							throw new MyParserException("Incorrect parameter format",lineNumber,0);
						}
					} 
			}
			else if (parameterName.equals("description"))
				description=trimmedLine.substring(trimmedLine.split("\\ ")[0].length()).trim();
			else
				throw new MyParserException("\""+parameterName+"\" is not a valid formal context parameter.",lineNumber,0);
			line = input.readLine();
			trimmedLine = line.trim();
			lineNumber++;
		}
		
		
		int currentRow = 0;
		ArrayList<String> attrs = new ArrayList<>();
		ArrayList<String> objs = new ArrayList<>();

		ArrayList<Pair> pairs=new ArrayList<>();
		while ( line != null ) {

			String tline = line.trim();
			if ( tline.startsWith("FormalContext") )
				break;
			else if ( tline.startsWith("RelationalContext") )
				break;
			else if ( tline.equals("") )
				break;
			
			String[] tokens = tline.split("\\|");
			int len = tokens.length;
			if ( currentRow == 0 ) {

				for(int i = 2 ; i < len ; i++ ) {
					String attrDesc = tokens[i].trim();
					attrs.add(attrDesc);
					
//					System.out.println("Attribute " + attrDesc + " created. Row: " + i);
				}
			}
			else {
				String name = tokens[1].trim();
                                objs.add(name);
//				System.out.println("Entity " + name + " created. Line: " + currentRow);
				for(int i = 2,numattr=0 ; i < len ; i++,numattr++ ) {
					String cell = tokens[i].trim().toLowerCase();
					if ( "x".equals(cell) ) {
						pairs.add(new Pair(currentRow-1,numattr));
//						System.out.println("Pair between " + objs.get(currentRow-1) + " and " + attrs.get(numattr) + " created. Line: " + currentRow);
					}
				}
			}

			currentRow++;

			input.mark(0);

			line = input.readLine();
			lineNumber++;
		}
                // build context
		IBinaryContext oac = new BinaryContext(objs.size(),attrs.size(),oacName,factory);
                for(String attrName:attrs)
                    oac.addAttributeName(attrName);
                for(String objName:objs)
                    oac.addObjectName(objName);
		for (Pair p: pairs)
			oac.set(p.e, p.a,true);
		try {
                    FormalContext fc=rcf.addFormalContext(oac,null);
		} catch (Exception e) {
			throw new MyParserException(e.getMessage(),oAContextLine,0);

		}

		if ( line != null )
			input.reset();

	}

	/**
	 * Parses the relational context.
	 *
	 * @param rcf the rcf
	 * @param input the input
	 * @param desc the desc
	 * @param factory the factory
	 * @throws Exception the exception
	 */
	private void parseRelationalContext(RCAFamily rcf, BufferedReader input,String desc,ISetFactory factory) throws Exception {
		
		String rcName = desc.split("\\ ")[1];
		
		
		int relationLineNumber=lineNumber;
		input.mark(0);
		String line = input.readLine();
		String trimmedLine = line.trim();
		lineNumber++;
		
		//parse parameters
		String description=null;
		String source=null;
		String target=null;
		String sclOp=null;
//		System.out.println("first line");
		while (!line.startsWith("|"))
		{
		
			String parameterName=trimmedLine.split("\\ ")[0];
			if (parameterName.equals("description")) {
				description=trimmedLine.substring(trimmedLine.split("\\ ")[0].length()).trim();
			} else if (trimmedLine.split("\\ ").length!=2)
				throw new MyParserException("invalid number of arguments.",lineNumber,0);
			else if (parameterName.equals("source")) {
				source=trimmedLine.split("\\ ")[1];
			} else if (parameterName.equals("target")) {
				target=trimmedLine.split("\\ ")[1];
			} else if (parameterName.equals("scaling")) {
				sclOp=trimmedLine.split("\\ ")[1];
			} else 
				throw new MyParserException("\""+parameterName+"\" is not a valid formal context paramater.",lineNumber,0);
			
			line = input.readLine();
			trimmedLine = line.trim();
			lineNumber++;
		}
		
		if (source==null)
			throw new MyParserException("Missing source of relation "+rcName,relationLineNumber,0);
		if (target==null)
			throw new MyParserException("Missing source of relation "+rcName,relationLineNumber,0);
		
		IBinaryContext sourceFc = rcf.getFormalContext(source).getContext();
		if (sourceFc==null)
			throw new IOException("source context \""+source+"\" of relational context \""+rcName+"\" cannot be found (line "+(lineNumber-2)+")");
		IBinaryContext targetFc = rcf.getFormalContext(target).getContext();
		if (targetFc==null)
			throw new IOException("target context \""+target+"\" of relational context \""+rcName+"\" cannot be found (line "+(lineNumber-1)+")");
		
		BinaryContext rc = new BinaryContext(sourceFc.getObjectCount(),targetFc.getObjectCount(),rcName,factory);
                // populate source and target object names
                for(int i=0;i<sourceFc.getObjectCount();i++)
                    rc.addObjectName(sourceFc.getObjectName(i));
                for(int i=0;i<targetFc.getObjectCount();i++)
                    rc.addAttributeName(targetFc.getObjectName(i));
                
                AbstractScalingOperator operator= MyScalingOperatorFactory.createScalingOperator(sclOp)	;	
                if (sclOp==null){
                    operator=new MyExistentialScaling();
                }
		if (description!=null)
			rc.setDescription(description);				
		int currentRow = 0;
		
		//table mapping the position of the parsed entities with the position in the target objectAttribute context
		int[] tgtEntsIndex= new int[targetFc.getObjectCount()];
				
//		System.out.println("remaining lines");
//		long debug_time1=System.currentTimeMillis()/1000;
				
		
		while ( line != null ) {
			String tline = line.trim();
			if ( tline.startsWith("RelationalContext") )
				break;
			else if ( tline.startsWith("FormalContext") )
				break;
			else if ( tline.equals("") )
				break;

			String[] tokens = tline.split("\\|");
			int len = tokens.length;
			if ( currentRow == 0 ) {

				for(int i = 2 ; i < len ; i++ ) {
					String name = tokens[i].trim();
					int entIndex = targetFc.getObjectIndex(name);
					if (entIndex==-1){
						System.out.println(name);
						throw new MyParserException("error relation "+rcName+".",lineNumber,0);
					}
					tgtEntsIndex[i-2]=entIndex;
				}
				
			}
			else {
				String name = tokens[1].trim();
				//index of the entity corresponding to the row in the source oacontext
				int rowEntityIndex = sourceFc.getObjectIndex(name);
				if (rowEntityIndex==-1){
					System.out.println(name);					
					throw new MyParserException("error relation "+rcName+".",lineNumber,0);
				}
				for(int i = 2 ; i < len ; i++ ) {
					String cell = tokens[i].trim().toLowerCase();
					if ( "x".equals(cell) ) {
						rc.set(rowEntityIndex, tgtEntsIndex[i-2],true);
					}
				}
			}

			currentRow++;

			input.mark(0);

			line = input.readLine();
			lineNumber++;
		}
//		long debug_time2=System.currentTimeMillis()/1000;
//		System.out.println("end of relational context ("+(debug_time2-debug_time1)+" s)");
		try {
			rcf.addRelationalContext(rc,sourceFc,targetFc,operator.getName());
		} catch (Exception e) {
			throw new MyParserException(e.getMessage(),relationLineNumber,0);
		}

		if ( line != null )
			input.reset();

	}
	
	/**
	 * The Class MyParserException.
	 *
	 * @author xdolques
	 */
	private class MyParserException extends Exception {
		
		/** The Constant serialVersionUID. */
		private static final long serialVersionUID = 1L;
		
		/** The msg. */
		private String msg;
		
		/** The line. */
		private int line;
		
		/** The offset. */
		private int offset;

		/**
		 * Instantiates a new my parser exception.
		 *
		 * @param msg the msg
		 * @param line the line
		 * @param offset the offset
		 */
		public MyParserException(String msg, int line, int offset){
			this.msg=msg;
			this.line=line;
			this.offset=offset;
		}

		/**
		 * Gets the message.
		 *
		 * @return the message
		 */
		@Override
		public String getMessage() {
			
			return msg+" Error at line: "+line+", char: "+offset;
		}
		
		
		
		
		
	}

}
