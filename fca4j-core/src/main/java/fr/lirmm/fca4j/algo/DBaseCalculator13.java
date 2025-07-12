package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import fr.lirmm.fca4j.algo.DBaseCalculator12.MinimalTransversalGenerator;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator13 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;
	protected List<Implication> dqBasis;

	public DBaseCalculator13(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		supportBidon = context.getFactory().createSet();
	}

	@Override
	public void run() {
		implications = computeDBasis();
	}

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	public List<Implication> computeDBasis() {
        List<Implication> dBasis = new ArrayList<>();

		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		dqBasis = linCbO.getResult();
		System.out.println("lincbo done");

		for (int target = 0; target < context.getAttributeCount(); target++) {
			System.out.println("traitement attr=" + target);
			List<ISet> counterExamples = getCounterexamplesFor(target);
            Set<ISet> generators = computeMinimalTransversals(counterExamples);

            for (ISet generator : generators) {
                if (isMinimalGenerator(generator, target)) {
                	ISet conclusion=context.getFactory().createSet();
                	conclusion.add(target);
                    dBasis.add(new Implication(generator.clone(), conclusion,0));
                }
            }
		}
		// Sort implication by cardinality
		Collections.sort(dBasis, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});
		return dBasis;
    }
	public List<ISet> getCounterexamplesFor(int b) {
	    List<ISet> counterExamples = new ArrayList<>();
	    for(int obj=0;obj<context.getObjectCount();obj++) {
	    	ISet attrs=context.getIntent(obj);
	    	if(!attrs.contains(b)) {
	    		counterExamples.add(attrs.clone());
	    	}
	        
	    }
	    return counterExamples;
	}

    private boolean isMinimalGenerator(ISet premise, int b) {
        ISet closureSet = RuleUtilities.computeClosure(premise, dqBasis);
        if (!closureSet.contains(b)) return false;

        for (Iterator<Integer> it=premise.iterator();it.hasNext();) {
        	int attr=it.next();
            ISet smaller = premise.clone();
            smaller.remove(attr);
            if (RuleUtilities.computeClosure(smaller, dqBasis).contains(b)) return false;
        }

        return true;
    }

    /**
     * Computes all minimal hitting sets (transversals) for a given hypergraph.
     */
    public Set<ISet> computeMinimalTransversals(List<ISet> hypergraph) {
        Set<ISet> result = new HashSet<>();
        computeTransversalsRecursive(hypergraph, 0, context.getFactory().createSet(), result);
        return result;
    }

    private void computeTransversalsRecursive(List<ISet> hypergraph, int index, ISet current, Set<ISet> result) {
        // Skip covers already hit
        while (index < hypergraph.size() && intersects(current, hypergraph.get(index))) {
            index++;
        }

        if (index == hypergraph.size()) {
            if (isMinimalInSet(current, result)) {
                result.removeIf(s -> s.containsAll(current)); // Remove supersets
                result.add(current.clone());
            }
            return;
        }

        ISet set = hypergraph.get(index);
        for (Iterator<Integer> it=set.iterator();it.hasNext();) {
        	int attr=it.next();
            if (!current.contains(attr)) {
                current.add(attr);
                computeTransversalsRecursive(hypergraph, index + 1, current, result);
                current.remove(attr);
            }
        }
    }

    private boolean intersects(ISet a, ISet b) {
        for (Iterator<Integer> it=a.iterator();it.hasNext();) {
        	int el=it.next();
            if (b.contains(el)) return true;
        }
        return false;
    }

    private boolean isMinimalInSet(ISet candidate, Set<ISet> existing) {
        for (ISet e : existing) {
            if (e.containsAll(candidate)) return false;
        }
        return true;
    }    
}
