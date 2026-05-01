/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/**
 * The Class UniversalReader.
 */
public class UniversalReader {
	
	/**
	 * Read.
	 *
	 * @param file the file
	 * @param sep the sep
	 * @return the i binary context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static IBinaryContext read(File file,char sep) throws IOException {
		return read(file, new BitSetFactory(),sep);
	}

	/**
	 * Read.
	 *
	 * @param file the file
	 * @param factory the factory
	 * @param sep the sep
	 * @return the i binary context
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static IBinaryContext read(File file, ISetFactory factory,char sep) throws IOException {
		BufferedReader buff = new BufferedReader(new FileReader(file));
		String line;
		ArrayList<String> objects=new ArrayList<>();
		ArrayList<String> attributes=new ArrayList	<>();
		ArrayList<Incidence> incidences=new ArrayList<>();
		while((line=buff.readLine())!=null)
		{
			String[] elms=line.split(""+sep);
			if(elms.length>0)
			{
				// object
				int numobj=objects.indexOf(elms[0]);
				if(numobj<0){
					objects.add(elms[0]);
					numobj=objects.size()-1;
				}
				for(int i=1;i<elms.length;i++){
					int numattr=attributes.indexOf(elms[i]);
					if(numattr<0){
						attributes.add(elms[i]);
						numattr=attributes.size()-1;
					}
					incidences.add(new Incidence(numobj,numattr));
				}
				
			}
		}
		buff.close();
		String contextName=file.getName().replaceAll("\\s+", "_");
		IBinaryContext context=new BinaryContext(objects.size(), attributes.size(), contextName, factory);
		for(int numobj=0;numobj<objects.size();numobj++)
			context.addObjectName(objects.get(numobj));
		for(int numattr=0;numattr<attributes.size();numattr++)
			context.addAttributeName(attributes.get(numattr));
		for(Incidence incidence:incidences)
			context.set(incidence.obj, incidence.attr, true);
		return context;
	}
    
    /**
     * The Class Incidence.
     */
    public static class Incidence {

        /** The obj. */
        int obj;
        
        /** The attr. */
        int attr;

        /**
         * Instantiates a new incidence.
         *
         * @param obj the obj
         * @param attr the attr
         */
        public Incidence(int obj,int attr) {
            this.obj = obj;
            this.attr = attr;
        }

    }
}
