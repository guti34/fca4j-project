/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
