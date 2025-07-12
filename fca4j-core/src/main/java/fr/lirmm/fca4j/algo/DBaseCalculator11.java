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

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator11 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;
	protected List<Implication> dqBasis;

	public DBaseCalculator11(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		supportBidon = context.getFactory().createSet();
	}

	@Override
	public void run() {
		implications = computeDBasis();
		System.out.println("computeMinimalGenerators " + chrono.getResult("computeMinimalGenerators"));
		System.out.println("extractCovers " + chrono.getResult("extractCovers"));
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

		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		dqBasis = linCbO.getResult();
		System.out.println("lincbo done");
		List<Implication> dBasis = new ArrayList<>();

		for (int target = 0; target < context.getAttributeCount(); target++) {
			System.out.println("traitement attr=" + target);
			chrono.start("computeMinimalGenerators");
			Set<ISet> minGens = computeMinimalGenerators(target);
			chrono.stop("computeMinimalGenerators");
			
			chrono.start("extractCovers");
			System.out.println("minGens="+minGens.size());
			Set<ISet> covers = extractCovers(minGens);
			chrono.stop("extractCovers");

			for (ISet premise : covers) {
				if(isValidImplication(premise, target)) {
				ISet conclusion = context.getFactory().createSet();
				conclusion.add(target);
				dBasis.add(new Implication(premise, conclusion, 0));
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
		// ⚠️ Optional: order D-basis according to dependency depth (topological sort)
//	        return sortImplications(dBasis);

	}

	public Set<ISet> computeMinimalGenerators(int b) {
		List<ISet> counterExamples = getCounterexamplesFor(b);

		return computeMinimalTransversals(counterExamples);
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
    public Set<ISet> computeMinimalTransversals(List<ISet> hypergraph) {
        Set<ISet> result = new HashSet<>();
        ISet current = context.getFactory().createSet();
        generate(current, hypergraph, result);
        return result;
    }

    private void generate(ISet current, List<ISet> hypergraph, Set<ISet> result) {
        // Trouver la première contrainte non satisfaite
        for (ISet constraint : hypergraph) {
        	if(current.newIntersect(constraint).isEmpty()) {
        		for(Iterator<Integer>it=constraint.iterator();it.hasNext();) {
        			int attr=it.next();
                        current.add(attr);
                        generate(current, hypergraph, result);
                        current.remove(attr);
                }
                return;
            }
        }

        // Vérifie la minimalité
        if (isMinimal(current, result)) {
            result.add(current.clone());
        }
    }
    private static boolean isMinimal(ISet candidate, Set<ISet> result) {
        for (ISet r : result) {
            if (candidate.containsAll(r)) return false;
        }
        return true;
    }
    
	public Set<ISet> extractCovers(Set<ISet> minGenerators) {
		Set<ISet> covers = new HashSet<>();

		for (ISet gen : minGenerators) {
			boolean isCover = true;
			for (Iterator<Integer> it = gen.iterator(); it.hasNext();) {
				int attr = it.next();
				ISet subset = gen.clone();
				subset.remove(attr);
				if (minGenerators.contains(subset)) {
					isCover = false;
					break;
				}
			}
			if (isCover)
				covers.add(gen);
		}

		return covers;
	}
	boolean isValidImplication(ISet premise, int conclusion) {
		for (int obj = 0; obj < context.getObjectCount(); obj++) {
			ISet intent = context.getIntent(obj);
			if (intent.containsAll(premise) && !intent.contains(conclusion))
				return false;
		}
		return true;
	}
}
