package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

public class DBaseCalculator5 implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	ClosureDirect closureEngine; 
	
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator5(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
	    ClosureDirect closureEngine=new ClosureDirect(context);
		LinCbO linCbO = new LinCbO(context, chrono,closureEngine , false);
		linCbO.run();
		System.out.println("linCbo done");
		List<Implication> dgBasis = linCbO.getResult();
		List<Implication> dBasis = new ArrayList<>();
		int counter=0;
        for (Implication dg : dgBasis) {
            ClosureTracer tracer = new ClosureTracer(dBasis);
            ISet closure = tracer.computeClosure(dg.getPremise());
            for(Iterator<Integer> it=dg.getConclusion().iterator();it.hasNext();) {
            	int conclusion=it.next();
	            if (!closure.contains(conclusion)) {
	                ISet minimalPremise = tracer.getReducedAttributes(dg.getPremise());
	                ISet conc=context.getFactory().createSet();
	                conc.add(conclusion);
	                Implication newImplication=new Implication(minimalPremise, conc,dg.getSupport());
//	                System.out.println("DBasis implication="+newImplication);
	                dBasis.add(newImplication);
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

        implications = dBasis;
	}

	
	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	public class ClosureTracer {
	    List<Implication> basis;
	    Deque<Implication> justification = new ArrayDeque<>();	   

	    public ClosureTracer(List<Implication> basis) {
	        this.basis = basis;
	    }

	protected ISet computeClosure(ISet attrs) {
	    ISet closure = attrs.clone();
	    boolean changed;
	    do {
	        changed = false;
	        for (Implication imp : basis) {
	            if (closure.containsAll(imp.getPremise())){
	            	if(closure.containsAll(imp.getConclusion())){
		                	justification.push(imp);
	            	}
	            	else {
		                closure.addAll(imp.getConclusion());
		                changed = true;	            		
	            	}
	            }
	            }
	    } while (changed);
	    return closure;
	}	

	    // Récupère les attributs d'origine ayant causé l'ajout de "target"
	    public ISet getReducedAttributes(ISet inputAttrs) {
	        ISet reducedIntent = inputAttrs.clone();
	        ISet visited = context.getFactory().createSet();
	        Deque<Implication> justification2=new ArrayDeque<>();
	        justification2.addAll(justification);
	        while (!justification2.isEmpty()) {
	            Implication current = justification2.pop();
	            if (visited.containsAll(current.getConclusion())) continue;
	            visited.add(current.getConclusion().iterator().next());

	    		if(reducedIntent.containsAll(current.getPremise())&& reducedIntent.containsAll(current.getConclusion())) {
				    		reducedIntent.removeAll(current.getConclusion());
	            }
	        }
	        return reducedIntent;
	    }
	    
	}}
