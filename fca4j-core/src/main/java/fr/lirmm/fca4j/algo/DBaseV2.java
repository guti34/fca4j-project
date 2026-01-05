package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

public class DBaseV2 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	
	/** The implications. */
	protected List<Implication> implications;

	public DBaseV2(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
	    supportBidon=createEmptySet();
	}
	private ISet createEmptySet() {
		return context.getFactory().createSet();
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

		List<Implication> initialBasis=buildInitialBasis(); 
		System.out.println("initial basis done");
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
	                    if (implA==implC) {
//		                    if (implA.equals(implC)) {
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
	                            initialBasis.add(new Implication(A, newConclusion,0));
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
	                            if(!implC.equals(newImpl))	                            
	                            	changed = true;
	                            break outer;
	                        }
	                    }
	                }
	            }
	        } while (changed);
System.out.println("Σsr= "+initialBasis.size());	 
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
//	            	sigmaDo.add(new Implication(A, B,supportBidon));
	                addImplication(new Implication(A, B,supportBidon),sigmaDo);
	            }
	        }
	        
System.out.println("sigmaDo="+sigmaDo.size());
return sigmaDo;
	}
	void addImplication(Implication impl,List<Implication> basis) {
		if(impl.getConclusion().cardinality()==1 && !basis.contains(impl))
			basis.add(impl);
		else {
			for(Iterator<Integer> it=impl.getConclusion().iterator();it.hasNext();)
			{
				ISet conclusion=createEmptySet();
				conclusion.add(it.next());
				Implication newImpl=new Implication(impl.getPremise(), conclusion,supportBidon);
				if(!basis.contains(newImpl))
					basis.add(newImpl);
			}
		}
	}
	List<Implication> buildInitialBasis(){
		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		return linCbO.getResult();
	}
}
