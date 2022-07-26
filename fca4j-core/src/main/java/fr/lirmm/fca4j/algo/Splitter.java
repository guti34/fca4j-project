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
package fr.lirmm.fca4j.algo;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.util.Chrono;

import java.util.Iterator;

import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

/**
 *
 * @author agutierr
 */
public class Splitter implements AbstractAlgo {

    private IBinaryContext matrix; //ressource de depart
    private Chrono chrono = null; // eventually a chrono to store execution time 
    /**
     * The graph.
     */
    protected SimpleGraph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class); 
    private int ATTR_COUNT; 
    
    public Splitter(IBinaryContext binCtx, Chrono chrono) {
        super();
        this.matrix = binCtx;
        this.chrono = chrono;
        ATTR_COUNT=matrix.getAttributeCount();
    }

    @Override
    public String getDescription() {
        return "Splitter";
    }

    @Override
    public Object getResult() {
        return null;
    }

    @Override
    public void run() {
        for(int attr=0;attr<ATTR_COUNT;attr++)
            graph.addVertex(attr);
        for(int obj=0;obj<matrix.getObjectCount();obj++)
        {
            int num_vertex_obj=obj+ATTR_COUNT;
            graph.addVertex(num_vertex_obj);
            Iterator<Integer> itIntent=matrix.getIntent(obj).iterator();
            while(itIntent.hasNext())
            {
                graph.addEdge(itIntent.next(),num_vertex_obj);
            }
        }
        BiconnectivityInspector inspector=new BiconnectivityInspector(graph);        
        System.out.println("biconnected "+inspector.isBiconnected());
    }
    
}
