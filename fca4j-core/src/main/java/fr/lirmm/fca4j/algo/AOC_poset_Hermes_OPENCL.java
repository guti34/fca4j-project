package fr.lirmm.fca4j.algo;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.gpu.OpenCLContext;
import fr.lirmm.fca4j.util.Chrono;
// TODO
public class AOC_poset_Hermes_OPENCL extends AOC_poset_Hermes {
   public AOC_poset_Hermes_OPENCL(IBinaryContext bc, Chrono chrono) {
        super(bc,chrono);
     }

    public AOC_poset_Hermes_OPENCL(IBinaryContext bc) {
        super(bc, null);
    }



    protected void computeHasseDiagram(ArrayList<ConceptSet> conceptSets) throws Exception {
        // sort concept sets depending on the cardinality
        Collections.sort(conceptSets, new Comparator<ConceptSet>() {

            @Override
            public int compare(ConceptSet o1, ConceptSet o2) {
                int card1 = o1.values.cardinality();
                int card2 = o2.values.cardinality();
                return -Integer.compare(card1, card2);
            }
        });
        // build data for opencl computation
        OpenCLContext openCLContext=new OpenCLContext(readProgramSource());     
        openCLContext.createKernel("computeHasseDiagram");
        ArrayList<ISet> intents=new ArrayList<>();
        for(ConceptSet conceptSet:conceptSets)
        {
        	intents.add(conceptSet.values);
        }
        //openCLContext.createMatrixBuffer(intents, nbAttr, CL_MEM_READ_ONLY);
        ArrayList<Integer> concepts = new ArrayList<>();
        ArrayList<ConceptSet> conceptSetArray = new ArrayList<>();
        for (int i = 0; i < conceptSets.size(); i++) {
            ConceptSet cSet = conceptSets.get(i);
        }

    }

    @Override
    public String getDescription() {
        return "HermesOPENCL";
    }
	private String readProgramSource() {
		String path = "/fca4j_core.cl";
		String program = null;
		try {
			InputStream inputStream = getClass().getResourceAsStream(path);
			StringBuilder textBuilder = new StringBuilder();
			try (Reader reader = new BufferedReader(
					new InputStreamReader(inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
				int c = 0;
				while ((c = reader.read()) != -1) {
					textBuilder.append((char) c);
				}
			}
			program = textBuilder.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return program;
	}
}
