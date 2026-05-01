/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.util.Chrono;

import java.util.Iterator;

import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

/**
 * The Class Splitter.
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
    
    /**
     * Instantiates a new splitter.
     *
     * @param binCtx the bin ctx
     * @param chrono the chrono
     */
    public Splitter(IBinaryContext binCtx, Chrono chrono) {
        super();
        this.matrix = binCtx;
        this.chrono = chrono;
        ATTR_COUNT=matrix.getAttributeCount();
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "Splitter";
    }

    /**
     * Gets the result.
     *
     * @return the result
     */
    @Override
    public Object getResult() {
        return null;
    }

    /**
     * Run.
     */
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
