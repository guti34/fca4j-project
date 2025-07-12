package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
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

public class DBaseCalculator2 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator2(IBinaryContext context) {
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

		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		List<Implication> dqBasis=linCbO.getResult(); 
		System.out.println("lincbo done");
	       boolean changed;
	        do {
	            changed = false;
	            // On travaille sur une copie de sigmaSr pour éviter les modifications concurrentes.
	            List<Implication> tempBasis = new ArrayList<>();
	            for(Implication impl:dqBasis) {
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
	                            dqBasis.remove(implA);
	                            dqBasis.remove(implC);
	                            dqBasis.add(new Implication(A, newConclusion,implA.getSupport()));
	                            changed = true;
	                            break outer;
	                        }
	                        // Sinon si D ⊆ B, supprimer l’implication C → D de sigmaSr.
	                        else if (B.containsAll(D)) {
	                        	dqBasis.remove(implC);
	                            changed = true;
	                            break outer;
	                        }
	                        // Sinon, remplacer C → D par (C - B) → (D - B).
	                        else {
	                            ISet newPremise = C.newDifference(B);
	                            ISet newConclusion = D.newDifference(B);
	                            Implication newImpl=new Implication(newPremise, newConclusion,supportBidon);
	                            dqBasis.remove(implC);
//	                            if(equals(implC,newImpl)) continue;
	                            dqBasis.add(newImpl);
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
            for(Implication impl:dqBasis) {
            	sigmaDsr.add(impl.clone());
            }
	        // Parcours des paires d’implications issues de sigmaDsr (à l’aide d’une copie pour éviter les modifications concurrentes)
            List<Implication> sigmaDsrCopy = new ArrayList<>();
            for(Implication impl:dqBasis) {
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
}
