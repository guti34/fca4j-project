/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 *
 * @author agutierr
 */
public class ClosureDirectWithForkJoinPool implements ClosureStrategy {
	private MODE computeIntentMODE,computeExtentMODE;
    private static final int DEFAULT_THRESHOLD = 50;
    protected IBinaryContext matrix;
    protected ISetFactory factory;
    protected int threshold=DEFAULT_THRESHOLD;

    public ClosureDirectWithForkJoinPool(IBinaryContext matrix) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
    }
    public ClosureDirectWithForkJoinPool(IBinaryContext matrix,int threshold) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.threshold=threshold;
    }

    @Override
    public ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent) {
        ISet extent = computeExtent(attrSet);
        fermeture.addAll(attrSet);
        ISet intent = computeIntent(extent);
        fermeture.addAll(intent);
        return extent;
    }

    public ISet computeIntent(ISet objects) {
        ComputeTask2 myRecursiveTask;
            myRecursiveTask = new ComputeTask2(objects,0, matrix.getAttributeCount(),false,MODE.CollectWithContainsAll);
        return ForkJoinPool.commonPool().invoke(myRecursiveTask);
    }
    public ISet computeExtent(ISet attributes) {
         ComputeTask2 myRecursiveTask;
        if (matrix.getObjectCount() < attributes.cardinality()) 
            myRecursiveTask = new ComputeTask2(attributes,0, matrix.getObjectCount(),true,MODE.CollectWithContainsAll);
        else
            myRecursiveTask = new ComputeTask2(attributes,0, matrix.getAttributeCount(),true,MODE.BuiltWithIntersection);
        return ForkJoinPool.commonPool().invoke(myRecursiveTask);
    }

    @Override
    public void init(Chrono chrono) {
    }

    @Override
    public String name() {
        return "ForkJoinPool Parallelism: "+ ForkJoinPool.commonPool().getParallelism();
    }

    @Override
    public void notify(Implication implication) {
    }
	@Override
	public int threshold() {
		return threshold;
	}


    private class ComputeTask2  extends RecursiveTask<ISet> {
        protected final int beg;
        protected final int end;
        protected final ISet elements;
        protected MODE mode;
        protected boolean forExtent;

        public ComputeTask2(ISet elements, int beg, int end,boolean forExtent,MODE mode) {
            this.beg = beg;
            this.end = end;
            this.elements = elements;
            this.mode=mode;
            this.forExtent=forExtent;
        }

        ISet computeByIntersection() {
            int size = forExtent ? matrix.getObjectCount() : matrix.getAttributeCount();
            ISet set = factory.createSet(size);
            set.fill(size);
            if (end - beg > threshold) {
                Collection<ComputeTask2> results = ForkJoinTask.invokeAll(createSubtasks());
                try {
                    for (ComputeTask2 task : results) {
                        set.retainAll(task.get());
                    }
                } catch (Exception ex) {
                    Logger.getLogger(ClosureDirectWithForkJoinPool.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                for (int i = beg; i < end; i++) {
                    if (elements.contains(i)) {
                        if (forExtent) {
                            set.retainAll(matrix.getExtent(i));
                        } else {
                            set.retainAll(matrix.getIntent(i));
                        }
                    }
                }
            }
            return set;
        }

        ISet computeByUnion() {
            int size = forExtent ? matrix.getObjectCount() : matrix.getAttributeCount();
            ISet set = factory.createSet(size);
            if (end - beg > threshold) {
                Collection<ComputeTask2> results = ForkJoinTask.invokeAll(createSubtasks());
                try {
                    for (ComputeTask2 task : results) {
                        set.addAll(task.get());
                    }
                } catch (Exception ex) {
                    Logger.getLogger(ClosureDirectWithForkJoinPool.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                for (int i = beg; i < end; i++) {
                    if (forExtent) {
                        if (matrix.getIntent(i).containsAll(elements)) {
                            set.add(i);
                        }
                    } else {
                        if (matrix.getExtent(i).containsAll(elements)) {
                            set.add(i);
                        }
                    }
                }
            }
            return set;

        }
        Collection<ComputeTask2> createSubtasks() {
            List<ComputeTask2> dividedTasks = new ArrayList<>();
            dividedTasks.add(new ComputeTask2(elements, beg, (beg + end) / 2,forExtent,mode));
            dividedTasks.add(new ComputeTask2(elements, (beg + end) / 2, end,forExtent,mode));
            return dividedTasks;
        }

        @Override
        protected ISet compute() {            
           switch(mode){
               case CollectWithContainsAll:
                   return computeByUnion();
               case BuiltWithIntersection:
                   return computeByIntersection();
           }
           return null;
        }
    }


    enum MODE {
        CollectWithContainsAll, BuiltWithIntersection
    };

	@Override
	public void setContext(IBinaryContext ctx) {
		matrix=ctx;
		
	}
	@Override
	public void shutdown() {
		
	}
}
