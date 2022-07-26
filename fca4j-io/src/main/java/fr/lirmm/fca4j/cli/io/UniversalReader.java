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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

public class UniversalReader {
	public static IBinaryContext read(File file,char sep) throws IOException {
		return read(file, new BitSetFactory(),sep);
	}

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
		IBinaryContext context=new BinaryContext(objects.size(), attributes.size(), file.getName(), factory);
		for(int numobj=0;numobj<objects.size();numobj++)
			context.addObjectName(objects.get(numobj));
		for(int numattr=0;numattr<attributes.size();numattr++)
			context.addAttributeName(attributes.get(numattr));
		for(Incidence incidence:incidences)
			context.set(incidence.obj, incidence.attr, true);
		return context;
	}
    public static class Incidence {

        int obj;
        int attr;

        public Incidence(int obj,int attr) {
            this.obj = obj;
            this.attr = attr;
        }

    }
}
