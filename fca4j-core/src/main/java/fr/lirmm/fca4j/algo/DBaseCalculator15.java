package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
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

import org.jgrapht.alg.util.Pair;

import fr.lirmm.fca4j.algo.DBaseV16.ClosureEngine;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator15 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;
	protected List<Implication> dqBasis;

	public DBaseCalculator15(IBinaryContext context) {
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
	public List<Implication> buildInitBase() {
		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		System.out.println("lincbo done");
		return linCbO.getResult();		
	}
	public List<Implication> computeDBasis() {
		dqBasis=buildInitBase();
		List<Implication> dBasis = new ArrayList<>();

		for (int target = 0; target < context.getAttributeCount(); target++) {
			System.out.println("traitement attr=" + target);
			chrono.start("computeMinimalGenerators");
			Set<ISet> minGens = computeMinimalGenerators(target);
			System.out.println("minGens="+minGens.size());
			chrono.stop("computeMinimalGenerators");
			chrono.start("extractCovers");
			System.out.println("mingens cardinality="+minGens.size());
			Set<ISet> covers = extractCovers(minGens);
			System.out.println("covers="+covers.size());
			chrono.stop("extractCovers");

			for (ISet premise : covers) {
				ISet conclusion = context.getFactory().createSet();
				conclusion.add(target);
				dBasis.add(new Implication(premise, conclusion, 0));
			}
		}
		// Sort implication by cardinality
		Collections.sort(dBasis, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});
		// filter redundants
		System.out.println("dBasis before"+dBasis.size());
	chrono.start("filter redundancy");
	List<Implication> directBase= buildDirectBase(dBasis);
		System.out.println("dBasis after"+directBase.size());
	chrono.stop("filter redundancy");
	return directBase;
//		return dBasis;
		// ⚠️ Optional: order D-basis according to dependency depth (topological sort)
//	        return sortImplications(dBasis);

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
	public Set<ISet> computeMinimalGenerators(int target) {
	    Antichain antichain = new Antichain();

	    ISet fullSet = context.getFactory().createSet();
	    fullSet.fill(context.getAttributeCount());
	    fullSet.remove(target);

	    Deque<Pair<ISet, Integer>> stack = new ArrayDeque<>();
	    stack.push(new Pair<>(context.getFactory().createSet(), 0));

	    while (!stack.isEmpty()) {
	        Pair<ISet, Integer> state = stack.pop();
	        ISet current = state.getFirst();
	        int index = state.getSecond();

	        if ( RuleUtilities.computeClosure(current, dqBasis).contains(target)) {
	            antichain.addIfMinimal(current);
	            continue;
	        }

	        for (int i = index; i < context.getAttributeCount(); i++) {
	            if (i == target || current.contains(i)) continue;

	            ISet next = current.clone();
	            next.add(i);
	            stack.push(new Pair<>(next, i + 1));
	        }
	    }
	    return antichain.getAll();
	}
	public class Antichain {
	    private final Set<ISet> generators = new HashSet<>();

	    public boolean addIfMinimal(ISet candidate) {
	        for (ISet existing : generators) {
	            if (candidate.containsAll(existing)) return false; // Not minimal
	        }

	        generators.removeIf(existing -> existing.containsAll(candidate));
	        generators.add(candidate.clone());
	        return true;
	    }

	    public Set<ISet> getAll() {
	        return generators;
	    }
	}
	public List<Implication> buildDirectBase(List<Implication> candidateBase) {
	    ClosureEngine engine = new ClosureEngine();
	    List<Implication> directBase = new ArrayList<>();

	    // On suppose que les implications sont triées par taille de prémisse
	    candidateBase.sort(Comparator.comparingInt(impl -> impl.getPremise().cardinality()));

	    for (Implication impl : candidateBase) {
	        ISet closure = engine.computeClosure(impl.getPremise());

	        if (!closure.containsAll(impl.getConclusion())||!RuleUtilities.isDirect(impl.getConclusion(), engine.getBase())) {
	            engine.add(impl);
	            directBase.add(impl);
	        }
	        // Sinon : l'implication est redondante et déjà couverte par la base actuelle
	    }

	    return directBase;
	}
	public class ClosureEngine {
	    private final Map<Integer, List<Implication>> indexByAttribute = new HashMap<>();
	    private final List<Implication> base = new ArrayList<>();

	    public ClosureEngine() {
	    }

	    public void add(Implication impl) {
	        base.add(impl);
	        for (Iterator<Integer> it = impl.getPremise().iterator(); it.hasNext();) {
	            int attr = it.next();
	            indexByAttribute.computeIfAbsent(attr, k -> new ArrayList<>()).add(impl);
	        }
	    }

	    public ISet computeClosure(ISet input) {
	        ISet closure = input.clone();
	        Deque<Integer> frontier = new ArrayDeque<>();
	        Set<Integer> visited = new HashSet<>();

	        for (Iterator<Integer> it = closure.iterator(); it.hasNext();) {
	            int attr = it.next();
	            frontier.add(attr);
	            visited.add(attr);
	        }

	        while (!frontier.isEmpty()) {
	            int current = frontier.poll();
	            List<Implication> candidates = indexByAttribute.get(current);
	            if (candidates == null) continue;

	            for (Implication impl : candidates) {
	                if (!closure.containsAll(impl.getPremise())) continue;
	                int conclusion = impl.getConclusion().first();
	                if (!closure.contains(conclusion)) {
	                    closure.add(conclusion);
	                    if (!visited.contains(conclusion)) {
	                        frontier.add(conclusion);
	                        visited.add(conclusion);
	                    }
	                }
	            }
	        }

	        return closure;
	    }

	    public boolean implies(ISet premise, ISet conclusion) {
	        ISet closure = computeClosure(premise);
	        return closure.containsAll(conclusion);
	    }

	    public List<Implication> getBase() {
	        return base;
	    }
	}
}
