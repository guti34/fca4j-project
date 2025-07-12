package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.core.IBinaryContext;		
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

public class DBaseCalculator3 implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	ClosureDirect closureEngine; 
	
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator3(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
	    ClosureDirect closureEngine=new ClosureDirect(context);
		LinCbO linCbO = new LinCbO(context, chrono,closureEngine , false);
		linCbO.run();
		List<Implication> result = linCbO.getResult();
		// convert to have atomic conclusions
		List<Implication> atomicBasis = new ArrayList<>();
		for(Implication impl:result) {
			if(impl.getConclusion().cardinality()==1)
				atomicBasis.add(impl);
			else {
				for(Iterator<Integer>it=impl.getConclusion().iterator();it.hasNext();) {
					ISet atomicConclusion=context.getFactory().createSet();
					atomicConclusion.add(it.next());
					atomicBasis.add(new Implication(impl.getPremise(),atomicConclusion,impl.getSupport()));
				}
			}
		}
		implications = computeDirectBase(atomicBasis);
	}

	
	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	List<Implication> computeDirectBase(List<Implication> canonicalBase) {
	    List<Implication> directBase = new ArrayList<>();
		for(int b=0;b<context.getAttributeCount();b++) {
			System.out.println("traitement attribut:"+b);
	        List<ISet> Fb = new ArrayList<>();

	        // Génère tous les sous-ensembles de Σ \ {b}
	        ISet attrWithoutB = context.getFactory().createSet();
	        attrWithoutB.fill(context.getAttributeCount());
	        attrWithoutB.remove(b);
	        List<ISet> allSubsets = generateAllSubsets(attrWithoutB);

	        for (ISet A : allSubsets) {
	            ISet closureA = computeClosure(A, canonicalBase);

	            if (!closureA.contains(b))
	                continue;

	            // Vérifie la minimalité de A pour b
	            boolean isMinimal = true;
	            for (ISet A2 : generateProperSubsets(A)) {
	                if (computeClosure(A2, canonicalBase).contains(b)) {
	                    isMinimal = false;
	                    break;
	                }
	            }

	            if (isMinimal)
	                Fb.add(A);
	        }
	        // Calcul des hitting sets minimaux 
	        Set<ISet> hittingSets = computeMinimalHittingSets(Fb);

	        for (ISet H : hittingSets) {
	            // Vérifie que b n'est pas déjà dans la fermeture de H selon la base partielle
	            if (!computeClosure(H, directBase).contains(b)) {
	            	ISet conclusion=context.getFactory().createSet();
	            	conclusion.add(b);
	                directBase.add(new Implication(H, conclusion,-1));
	            }
	        }
	    }

	    return directBase;
	}
	
	protected ISet computeClosure(ISet attrs, List<Implication> base) {
	    ISet closure = attrs.clone();
	    boolean changed;
	    do {
	        changed = false;
	        for (Implication imp : base) {
	            if (closure.containsAll(imp.getPremise()) && !closure.containsAll(imp.getConclusion())) {
	                closure.addAll(imp.getConclusion());
	                changed = true;
	            }
	        }
	    } while (changed);
	    return closure;
	}	
	
	protected List<ISet> generateAllSubsets(ISet set) {
	    List<ISet> subsets = new ArrayList<>();
	    for (int i = 0; i < (1 << set.cardinality()); i++) {
	        ISet subset = context.getFactory().createSet();
	        for (int j = 0; j < set.cardinality(); j++) {
	            if ((i & (1 << j)) > 0)
	                subset.add(j);
	        }
	        subsets.add(subset);
	    }
	    return subsets;
	}

	protected List<ISet> generateProperSubsets(ISet set) {
	    List<ISet> all = generateAllSubsets(set);
	    all.removeIf(s -> s.cardinality() == set.cardinality());
	    return all;
	}
	/**
	 * Compute the minimal hitting sets for a family of subsets.
	 * 
	 * @param family a list of subset (F elements)
	 * @return The set of all minimal hitting sets
	 */
	public Set<ISet> computeMinimalHittingSets(List<ISet> family) {
		// Si la famille est vide, l'ensemble vide est le seul hitting set.
		if (family.isEmpty()) {
			Set<ISet> result = new HashSet<>();
			result.add(context.getFactory().createSet());
			return result;
		}

		// Choisir arbitrairement un ensemble S dans la famille
		ISet S = family.get(0);
		Set<ISet> hittingSets = new HashSet<>();

		// Pour chaque attribut x de S, on construit récursivement des hitting sets
		for (Iterator<Integer> it = S.iterator(); it.hasNext();) {
			int x = it.next();
			// F_x = { T ∈ family : x ∉ T } (sets covered by x are removed)
			List<ISet> Fx = new ArrayList<>();
			for (ISet T : family) {
				if (!T.contains(x)) {
					Fx.add(T);
				}
			}
			// compute recursively hitting sets for Fx
			Set<ISet> Hx = computeMinimalHittingSets(Fx);
			// for each we add x
			for (ISet h : Hx) {
				ISet hPrime = h.clone();
				hPrime.add(x);
				hittingSets.add(hPrime);
			}
		}
//System.out.println("hitting sets="+hittingSets.size());	    
		// Pruning : remove from hittingSets all non minimal sets
		Set<ISet> minimals = filterMinimal(hittingSets);
//System.out.println("minimals="+minimals.size());	    
		return minimals;
	}

	private Set<ISet> filterMinimal(Set<ISet> sets) {
//		System.out.println("set cardinality=" + sets.size());
		Set<ISet> minimal = new HashSet<>();
		for (ISet s : sets) {
			boolean isMinimal = true;
			for (ISet t : sets) {
				if (t != s && s.containsAll(t)) {
					isMinimal = false;
					break;
				}
			}
			if (isMinimal)
				minimal.add(s);
		}
		return minimal;
	}
}
