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

public class DBaseCalculator12 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;
	protected List<Implication> dqBasis;

	public DBaseCalculator12(IBinaryContext context) {
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

		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		dqBasis = linCbO.getResult();
		System.out.println("lincbo done");
		List<Implication> dBasis = new ArrayList<>();

		for (int target = 0; target < context.getAttributeCount(); target++) {
			System.out.println("traitement attr=" + target);
            List<ISet> covers = extractCovers(target);
            if (covers.isEmpty()) continue;
System.out.println("covers="+covers);
            Set<ISet> generators = MinimalTransversalGenerator.computeMinimalTransversals(covers,context.getFactory());
System.out.println("generators="+generators);

            for (ISet g : generators) {
                if (isMinimalGenerator(g, target)) {
    				ISet conclusion = context.getFactory().createSet();
    				conclusion.add(target);
                    dBasis.add(new Implication(g.clone(), conclusion,0));
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
    private List<ISet> removeRedundantCounterexamples(List<ISet> sets) {
        List<ISet> result = new ArrayList<>();
        for (ISet s : sets) {
            boolean subsumed = false;
            for (ISet other : sets) {
                if (!other.equals(s) && other.containsAll(s)) {
                    subsumed = true;
                    break;
                }
            }
            if (!subsumed) {
                result.add(s);
            }
        }
        return result;
    }
    private List<ISet> removeRedundantCovers(List<ISet> covers) {
        List<ISet> result = new ArrayList<>();
        for (ISet s : covers) {
            boolean subsumed = false;
            for (ISet other : covers) {
                if (!other.equals(s) && other.containsAll(s)) {
                    subsumed = true;
                    break;
                }
            }
            if (!subsumed) {
                result.add(s);
            }
        }
        return result;
    }
	
    private boolean isMinimalGenerator(ISet candidate, int b) {
        ISet closure = RuleUtilities.computeClosure(candidate, dqBasis);
        if (!closure.contains(b)) return false;
        for(Iterator<Integer> it=candidate.iterator();it.hasNext();) {
        	int attr=it.next();
            ISet subset = candidate.clone();
            subset.remove(attr);
            ISet subsetClosure = RuleUtilities.computeClosure(subset, dqBasis);
            if (subsetClosure.contains(b)) return false;
        }
        return true;
    }
    
    private List<ISet> extractCovers(int b) {
        List<ISet> covers = new ArrayList<>();
        for (Implication impl : dqBasis) {
            if (impl.getConclusion().contains(b)) {
                ISet premise = impl.getPremise().clone();
                premise.remove(b); // par sécurité
                covers.add(premise);
            }
        }
        return removeRedundantCovers(covers);
    }
    public static class MinimalTransversalGenerator {

        public static Set<ISet> computeMinimalTransversals(List<ISet> hypergraph, ISetFactory factory) {
            return compute(hypergraph, factory);
        }

        private static Set<ISet> compute(List<ISet> hypergraph, ISetFactory factory) {
            Set<ISet> result = new HashSet<>();

            // Cas de base : hypergraphe vide ⇒ seul transversal est l’ensemble vide
            if (hypergraph.isEmpty()) {
                result.add(factory.createSet());
                return result;
            }

            // Choix d’une contrainte
            ISet first = hypergraph.iterator().next();

            for (Iterator<Integer>it=first.iterator();it.hasNext();) {
            	int attr=it.next();
                // Filtrer les contraintes qui ne contiennent pas attr
                List<ISet> reduced = new ArrayList<>();
                for (ISet c : hypergraph) {
                    if (!c.contains(attr)) {
                        reduced.add(c);
                    }
                }

                // Appel récursif
                Set<ISet> partials = compute(reduced, factory);
                for (ISet partial : partials) {
                    ISet withAttr = partial.clone();
                    withAttr.add(attr);
                    result.add(withAttr);
                }
            }

            return minimize(result, factory); // optionnel mais conseillé
        }

        // Élimine les sur-ensembles (optionnel si garanties en amont)
        private static Set<ISet> minimize(Set<ISet> sets, ISetFactory factory) {
            List<ISet> sorted = new ArrayList<>(sets);
            sorted.sort(Comparator.comparingInt(ISet::cardinality)); // plus petits d'abord

            Set<ISet> result = new HashSet<>();
            for (ISet s : sorted) {
                boolean subsumed = false;
                for (ISet r : result) {
                    if (s.containsAll(r)) {
                        subsumed = true;
                        break;
                    }
                }
                if (!subsumed) {
                    result.add(s);
                }
            }
            return result;
        }
    }
    
}
