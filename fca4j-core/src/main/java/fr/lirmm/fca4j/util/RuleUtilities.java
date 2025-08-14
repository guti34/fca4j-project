package fr.lirmm.fca4j.util;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

public class RuleUtilities {
	/**
	 * Verify if an attribute set can be deduced from a base attribute set and a set
	 * of implications d'implications.
	 *
	 * @param attributes   Attribute set to verify
	 * @param base         Initial attribute set (prémise).
	 * @param dependencies A list of implications.
	 * @return true if the set can be deduced.
	 */
	public static boolean isDerivable(ISet attributes, ISet base, List<Implication> dependencies) {
		ISet closure = computeClosure(base, dependencies);
		boolean ret = closure.containsAll(attributes);
		return ret;
	}
	/**
	 * Verify if an attribute can be deduced from a base attribute set and a set
	 * of implications d'implications.
	 *
	 * @param attributs   Attribute to verify
	 * @param base         Initial attribute set (prémise).
	 * @param dependencies A list of implications.
	 * @return true if the set can be deduced.
	 */
	public static boolean isDerivable(int attribute, ISet base, List<Implication> dependencies) {
		ISet closure = computeClosure(base, dependencies);
		boolean ret = closure.contains(attribute);
		return ret;
	}

	/**
	 * Verify if a list of implication is direct
	 * 
	 * @param dependencies
	 * @return
	 */
	public static boolean isDirect(List<Implication> implications, ISetFactory factory) {
		return isDirect(implications, -1, factory);
	}
		public static boolean isDirect(List<Implication> implications, int maxCardinality, ISetFactory factory) {
		ISet attributes = factory.createSet();
		for (Implication impl : implications) {
			attributes.addAll(impl.getPremise());
			attributes.addAll(impl.getConclusion());
		}
		int last = -1;
		for (Iterator<Integer> it = attributes.iterator(); it.hasNext();) {
			last = it.next();
		}
		if (last > 0) {
			List<ISet> powerSet;
			if(maxCardinality<=0) powerSet= generatePowerSet(last, factory);
			else powerSet=generatePowerSet(last, maxCardinality,factory);
			for (ISet set : powerSet) {
				if (!isDirect(set, implications)) {
					System.out.println(set);
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Verify if a set of attributes reach his closure in one pass
	 * 
	 * @param attributes
	 * @param dependencies
	 * @return
	 */
	public static boolean isDirect(ISet attributes, List<Implication> dependencies) {
		ISet closure = attributes.clone();
		boolean changed;
		int count = 0;
		do {
			changed = false;
			for (Implication dep : dependencies) {
				if (closure.containsAll(dep.getPremise())) {
					if (!closure.containsAll(dep.getConclusion())) {
						closure.addAll(dep.getConclusion());
						changed = true;
					}
				}
			}
			if (changed)
				if(++count>1) return false;
		} while (changed);
		return true;
	}

	public static boolean isDirectWithStats(List<Implication> implications, ISetFactory factory) {
		int count = 0;
		ISet attributes = factory.createSet();
		for (Implication impl : implications) {
			attributes.addAll(impl.getPremise());
			attributes.addAll(impl.getConclusion());
		}
		int last = -1;
		for (Iterator<Integer> it = attributes.iterator(); it.hasNext();) {
			last = it.next();
		}
		if (last > 0) {
			List<ISet> powerSet = generatePowerSet(last, factory);
			for (ISet set : powerSet) {
				if (!isDirect(set, implications)) {
//					System.out.println(set);
					count++;
				}
			}
		}
		if (count > 0)
			System.out.println("Not direct: " + count);
		return count == 0;
	}

	/**
	 * Calcule la fermeture d'un ensemble d'attributs sous un ensemble
	 * d'implications.
	 *
	 * @param attributes   Ensemble d'attributs de départ.
	 * @param dependencies Liste des implications à appliquer.
	 * @return La fermeture de l'ensemble d'attributs.
	 */
	public static ISet computeClosure(ISet attributes, List<Implication> dependencies) {
		ISet closure = attributes.clone();
		boolean changed;

		do {
			changed = false;
			for (Implication dep : dependencies) {
				if (closure.containsAll(dep.getPremise())) {
					if (!closure.containsAll(dep.getConclusion())) {
						closure.addAll(dep.getConclusion());
						changed = true;
					}
				}
			}
		} while (changed);

		return closure;
	}
	/**
	 * Calcule la fermeture d'un ensemble d'attributs sous un ensemble
	 * d'implications en une seule passe.
	 *
	 * @param attributes   Ensemble d'attributs de départ.
	 * @param dependencies Liste des implications à appliquer.
	 * @return La fermeture de l'ensemble d'attributs.
	 */
	public static ISet computeDirectClosure(ISet attributes, List<Implication> dependencies) {
		ISet closure = attributes.clone();
			for (Implication dep : dependencies) {
				if (closure.containsAll(dep.getPremise())) {
					if (!closure.containsAll(dep.getConclusion())) {
						closure.addAll(dep.getConclusion());
					}
				}
			}
		return closure;
	}

	public static boolean isIncludedIn(List<Implication> subBasis, List<Implication> basis) {
		boolean derivable=true;
		for (Implication dep : subBasis) {
			ISet closure = computeClosure(dep.getPremise(), basis);
			ISet minimalConclusion = dep.getConclusion().newDifference(closure);

			if (!isDerivable(minimalConclusion, dep.getPremise(), basis)) {
//				System.out.println(dep.toString() + " is not derivable");
				derivable=false;
				break;
			}
		}
		return derivable;
	}

	/**
	 * Prints the implications.
	 *
	 * @param printWriter  the print writer
	 * @param implications the implications
	 */
	public static void printImplications(PrintWriter printWriter, Iterable<Implication> implications,
			IBinaryContext context) {
		for (Implication implication : implications) {
			int support = implication.getSupport() == null ? implication.getSupportSize()
					: implication.getSupport().cardinality();
			printWriter.printf("<%d> %s => %s\n", support, displayAttrs(implication.getPremise(), context),
					displayAttrs(implication.getConclusion(), context));
		}
		printWriter.close();
	}

	public static void printImplications(Iterable<Implication> implications, IBinaryContext context) {
		for (Implication implication : implications) {
			System.out.printf("<%d> %s => %s\n", implication.getSupport().cardinality(),
					displayAttrs(implication.getPremise(), context),
					displayAttrs(implication.getConclusion(), context));
		}

	}

	private static String displayAttrs(ISet set, IBinaryContext context) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			if (sb.length() != 0) {
				sb.append(",");
			}
			sb.append(context.getAttributeName(it.next()));
		}
		return sb.toString();
	}

	public static List<ISet> generateClosures(List<Implication> implications, ISetFactory factory) {
		ISet attributes = factory.createSet();
		for (Implication impl : implications) {
			attributes.addAll(impl.getPremise());
			attributes.addAll(impl.getConclusion());
		}
		int last = -1;
		for (Iterator<Integer> it = attributes.iterator(); it.hasNext();) {
			last = it.next();
		}
		HashSet<ISet> closures = new HashSet<>();
		if (last > 0) {
			List<ISet> powerSet = generatePowerSet(last + 1, factory);
			for (ISet set : powerSet) {
				closures.add(computeClosure(set, implications));
			}
		}
		return new ArrayList<ISet>(closures);
	}

	private static List<ISet> generatePowerSet(int N, ISetFactory factory) {
		List<ISet> powerSet = new ArrayList<>();
		int totalSubsets = 1 << (N + 1); // 2^(N+1)

		for (int i = 0; i < totalSubsets; i++) {
			ISet subset = factory.createSet();
			for (int j = 0; j <= N; j++) {
				if ((i & (1 << j)) != 0) {
					subset.add(j);
				}
			}
			powerSet.add(subset);
		}

		return powerSet;
	}
	private static List<ISet> generatePowerSet(int N, int maxCardinality, ISetFactory factory) {
	    List<ISet> powerSet = new ArrayList<>();
	    int totalSubsets = 1 << (N + 1); // 2^(N+1)

	    for (int i = 0; i < totalSubsets; i++) {
	        ISet subset = factory.createSet();
	        int count = 0;

	        for (int j = 0; j <= N; j++) {
	            if ((i & (1 << j)) != 0) {
	                subset.add(j);
	                count++;
	                if (count > maxCardinality) {
	                    break; // On arrête dès que la cardinalité est dépassée
	                }
	            }
	        }

	        if (count <= maxCardinality) {
	            powerSet.add(subset);
	        }
	    }

	    return powerSet;
	}
	public static Iterator<Implication> iteratorImplications(Collection<Implication> implications) {
		SimpleDirectedGraph<Implication, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
		for (Implication impl : implications) {
			graph.addVertex(impl);
		}
		for (Implication impl : graph.vertexSet()) {
			for (Implication impl2 : graph.vertexSet()) {
				if (impl != impl2 && impl.getPremise().containsAll(impl2.getConclusion())) {
					graph.addEdge(impl2, impl);
				}
			}
		}
		org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector<Implication, DefaultEdge> sci = new GabowStrongConnectivityInspector<>(graph);
		List<Set<Implication>> stronglyConnectedSets = sci.stronglyConnectedSets();

		// Affichage
		for (Set<Implication> component : stronglyConnectedSets) {
		    System.out.println("connected set : " + component.size());
		}		
		return new TopologicalOrderIterator<Implication, DefaultEdge>(graph);
	}

}
