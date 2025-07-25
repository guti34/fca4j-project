package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

import fr.lirmm.fca4j.algo.DBaseV16.ClosureEngine;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator2_16 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator2_16(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
	    supportBidon=context.getFactory().createSet();
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

/*		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		List<Implication> initialBasis=linCbO.getResult(); 
		System.out.println("lincbo done");
*/		
		List<Implication> initialBasis=computeDirectUnitBasis(); 
		System.out.println("direct unit basis done");
	       boolean changed;
	        do {
	            changed = false;
	            // On travaille sur une copie de sigmaSr pour éviter les modifications concurrentes.
	            List<Implication> tempBasis = new ArrayList<>();
	            for(Implication impl:initialBasis) {
	            	tempBasis.add(impl.clone());
	            }
	            outer:
	            for (Implication implA : tempBasis) {
	            	
	                ISet A = implA.getPremise().clone();
	                ISet B = implA.getConclusion().clone();
	                for (Implication implC : tempBasis) {
	                    if (equals(implA,implC)) {
	                        continue; // Évite de comparer une implication avec elle-même.
	                    }
	                    ISet C = implC.getPremise().clone();
	                    ISet D = implC.getConclusion().clone();
	                    // if A ⊆ C
	                    if (C.containsAll(A)) {
	                        // Si C ⊆ A ∪ B, remplacer les deux implications par A → B ∪ D.
	                        ISet unionAB = A.clone();
	                        unionAB.addAll(B);
	                        if (unionAB.containsAll(C)) {
	                            ISet newConclusion = B.clone();
	                            newConclusion.addAll(D);
	                            initialBasis.remove(implA);
	                            initialBasis.remove(implC);
	                            initialBasis.add(new Implication(A, newConclusion,implA.getSupport()));
	                            changed = true;
	                            break outer;
	                        }
	                        // Sinon si D ⊆ B, supprimer l’implication C → D de sigmaSr.
	                        else if (B.containsAll(D)) {
	                        	initialBasis.remove(implC);
	                            changed = true;
	                            break outer;
	                        }
	                        // Sinon, remplacer C → D par (C - B) → (D - B).
	                        else {
	                            ISet newPremise = C.newDifference(B);
	                            ISet newConclusion = D.newDifference(B);
	                            Implication newImpl=new Implication(newPremise, newConclusion,supportBidon);
	                            initialBasis.remove(implC);
//	                            if(equals(implC,newImpl)) continue;
	                            initialBasis.add(newImpl);
	                            if(!equals(implC,newImpl))	                            
	                            	changed = true;
	                            break outer;
	                        }
	                    }
	                }
	            }
	        } while (changed);
	        
System.out.println("Complétion de Σsr pour obtenir Σdsr");
	        // Stage 3 : 
            List<Implication> sigmaDsr = new ArrayList<>();
            for(Implication impl:initialBasis) {
            	sigmaDsr.add(impl.clone());
            }
	        // Parcours des paires d’implications issues de sigmaDsr (à l’aide d’une copie pour éviter les modifications concurrentes)
            List<Implication> sigmaDsrCopy = new ArrayList<>();
            for(Implication impl:initialBasis) {
            	sigmaDsrCopy.add(impl.clone());
            }
	        for (Implication implA : sigmaDsrCopy) {
	            ISet A = implA.getPremise().clone();
	            ISet B = implA.getConclusion().clone();
	            for (Implication implC : sigmaDsrCopy) {
	                ISet C = implC.getPremise().clone();
	                ISet D = implC.getConclusion().clone();
	                // Si B ∩ C ≠ ∅ et D - (A ∪ B) ≠ ∅, ajouter (A ∪ C - B) → (D - (A ∪ B)) à sigmaDsr.
	                ISet intersection = B.newIntersect(C);
	                ISet unionAB = A.clone();
	                unionAB.addAll(B);
	                ISet diffD = D.newDifference(unionAB);
	                if (!intersection.isEmpty() && !diffD.isEmpty()) {
	                    ISet newPremise = A.clone();
	                    newPremise.addAll(C);
	                    newPremise.removeAll(B);
	                    sigmaDsr.add(new Implication(newPremise, diffD,supportBidon));
	                }
	            }
	        }
        	System.out.println("sigmaDsr="+sigmaDsr.size());
	        // Stage 4 : Optimisation de Σdsr pour obtenir Σdo
	        List<Implication> sigmaDo = new ArrayList<>();
	        for (Implication impl : sigmaDsr) {
	            ISet A = impl.getPremise().clone();
	            ISet B = impl.getConclusion().clone(); // copie de la conclusion
	            for (Implication implC : sigmaDsr) {
	                // Si les prémisses sont égales, on fait l’union des conclusions.
	                if (implC.getPremise().equals(A)) {
	                    B.addAll(implC.getConclusion());
	                }
	                // Si la prémisse de implC est strictement incluse dans A, retirer ses attributs de la conclusion.
	                if(A.containsAll(implC.getPremise()) && (A.cardinality()>implC.getPremise().cardinality())){
	                    B.removeAll(implC.getConclusion());
	                }
	            }
	            if (!B.isEmpty()) {
	            	sigmaDo.add(new Implication(A, B,supportBidon));
//	                addImplication(new Implication(A, B,supportBidon),sigmaDo);
	            }
	        }
        	System.out.println("sigmaDo="+sigmaDo.size());
			// Étape 2 : Réduire les implications pour obtenir une D-base minimale
			List<Implication> minimalDBase = new ArrayList<>();
			List<Implication> sortedList = new ArrayList<>(sigmaDo);
			// Sort implication by cardinality
			Collections.sort(sortedList, new Comparator<Implication>() {
				@Override
				public int compare(Implication a1, Implication a2) {
					return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
				}
			});

			for (Implication dep : sortedList) {
				ISet closure = RuleUtilities.computeClosure(dep.getPremise(), minimalDBase);
				ISet minimalConclusion = dep.getConclusion().newDifference(closure);

				if (!RuleUtilities.isDerivable(minimalConclusion, dep.getPremise(), minimalDBase)) {
					addImplication(new Implication(dep.getPremise(), minimalConclusion, dep.getSupport()),minimalDBase);
//					minimalDBase.add(new Implication(dep.getPremise(), minimalConclusion, dep.getSupport()));
				}
			}
        	System.out.println("minimalDBase="+minimalDBase.size());
        
		return minimalDBase;
	}
	
	
	boolean equals(Implication implA,Implication implB) {
        return implA.getPremise().equals(implB.getPremise())&&implA.getConclusion().equals(implB.getConclusion());
		
	}
	void addImplication(Implication impl,List<Implication> basis) {
		if(impl.getConclusion().cardinality()==1 && !basis.contains(impl))
			basis.add(impl);
		else {
			for(Iterator<Integer> it=impl.getConclusion().iterator();it.hasNext();)
			{
				ISet conclusion=context.getFactory().createSet();
				conclusion.add(it.next());
				Implication newImpl=new Implication(impl.getPremise(), conclusion,supportBidon);
				if(!basis.contains(newImpl))
					basis.add(newImpl);
			}
		}
	}
		public List<Implication> computeDirectUnitBasis() {

			List<Implication> binaryBasis = new ArrayList<>();
			// find all binary implications
			for (int attr1 = 0; attr1 < context.getAttributeCount(); attr1++)
				for (int attr2 = 0; attr2 < context.getAttributeCount(); attr2++) {
					if (attr1 == attr2)
						continue;
					boolean valid = true;
					int support = 0;
					if (context.getExtent(attr2).containsAll(context.getExtent(attr1))) {
						ISet premise = context.getFactory().createSet();
						premise.add(attr1);
						ISet conclusion = context.getFactory().createSet();
						conclusion.add(attr2);
						binaryBasis.add(new Implication(premise, conclusion, context.getExtent(attr1)));
					}
				}
			List<Implication> dBasis = new ArrayList<>(binaryBasis);

			for (int target = 0; target < context.getAttributeCount(); target++) {
				List<Implication> tempBasis=new ArrayList<>(binaryBasis);
				System.out.println("traitement attr=" + target);
				chrono.start("computeMinimalGenerators");
				Set<ISet> minGens = computeMinimalGenerators(target);
				System.out.println("minGens=" + minGens.size());
				chrono.stop("computeMinimalGenerators");
				chrono.start("extractCovers");
				Set<ISet> covers = extractCovers(minGens);
				chrono.stop("extractCovers");
				System.out.println("covers=" + covers.size());
				chrono.start("verify validity");
				for (ISet premise : covers) {
					ISet conclusion = context.getFactory().createSet();
					conclusion.add(target);
					tempBasis.add(new Implication(premise, conclusion, 0));
				}
				System.out.println("tempBasis before"+tempBasis.size());
				// Sort implication by cardinality
				Collections.sort(tempBasis, new Comparator<Implication>() {
					@Override
					public int compare(Implication a1, Implication a2) {
						return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
					}
				});
				tempBasis=buildDirectBase(tempBasis);
				System.out.println("tempBasis after"+tempBasis.size());
				tempBasis.removeAll(binaryBasis);
				dBasis.addAll(tempBasis);
				chrono.stop("verify validity");
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
	
//			return dBasis;
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

		public Set<ISet> computeMinimalGenerators(int b) {
			List<ISet> hypergraph = new ArrayList<>();

			// Étape 1 : récupérer les objets ne contenant pas b (non-supports)
			ISet objectsWithoutB = context.getFactory().createSet();
			objectsWithoutB.fill(context.getObjectCount());
			objectsWithoutB.removeAll(context.getExtent(b));
			// build hypergraph
			for (Iterator<Integer> it = objectsWithoutB.iterator(); it.hasNext();) {
				int o = it.next();
				ISet edge = context.getFactory().createSet();
				edge.fill(context.getAttributeCount()); // M
				edge.removeAll(context.getIntent(o)); // M \ intent(o)
				edge.remove(b);
				hypergraph.add(edge);
			}

			// Étape 2 : appel au générateur de transversaux minimaux
			Set<ISet> result = new HashSet<>();
			ISet current = context.getFactory().createSet();
			generateTransversals(hypergraph, 0, current, result);
			return result;
		}

		private void generateTransversals(List<ISet> hypergraph, int index, ISet current, Set<ISet> result) {
			// Si tous les hyperarêtes sont frappées : on a un transversal
			if (index == hypergraph.size()) {
				result.add(current.clone());
				return;
			}

			ISet edge = hypergraph.get(index);
			// Si l’arête est déjà frappée → passer à la suivante
			if (!current.newIntersect(edge).isEmpty()) {
				generateTransversals(hypergraph, index + 1, current, result);
				return;
			}

			// Sinon : pour chaque attribut de l’arête, essayer de l’ajouter
			for (Iterator<Integer> it = edge.iterator(); it.hasNext();) {
				int attr = it.next();
				if (current.contains(attr))
					continue;
				current.add(attr);
				generateTransversals(hypergraph, index + 1, current, result);
				current.remove(attr);
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
