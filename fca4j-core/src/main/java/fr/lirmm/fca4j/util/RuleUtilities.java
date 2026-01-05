package fr.lirmm.fca4j.util;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.BiconnectivityInspector;
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

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
	 * Verify if an attribute can be deduced from a base attribute set and a set of
	 * implications d'implications.
	 *
	 * @param attributs    Attribute to verify
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
			if (maxCardinality <= 0)
				powerSet = generatePowerSet(last, factory);
			else {
				Chrono chrono = new Chrono("powerset");
				chrono.start("main");
				ArrayList<Integer> base = new ArrayList<>();
				for (int i = 0; i < last + 1; i++)
					base.add(i);
				powerSet = generatePowerSet(last, maxCardinality, factory);
				// on enleve l'ensemble vide
				assert powerSet.get(0).isEmpty();
				powerSet.remove(0);
				chrono.stop("main");
//				System.out.println("N=" + last + " maxCardinality=" + maxCardinality);
//				System.out.println("powerset size=" + powerSet.size());
			}
			try {
				return isDirect(powerSet, implications, factory.createSet());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
			/*
			 * for (ISet set : powerSet) { if (!isDirect(set, implications)) return false; }
			 */ }
		return true;
	}

	private static boolean isDirect(List<ISet> powerSet, List<Implication> basis, ISet POISON_PILL)
			throws InterruptedException {
		AtomicBoolean direct = new AtomicBoolean(true);
		BlockingQueue<ISet> queue = new LinkedBlockingQueue<>();
		int nbThreads = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newFixedThreadPool(nbThreads);
		// Lancer les workers
		for (int i = 0; i < nbThreads; i++) {
			executor.submit(() -> {
				try {
					while (true) {
						ISet set = queue.take();
						if (set == POISON_PILL) {
							break; // Fin pour ce worker
						}
						// Calculer et stocker
						boolean success = isDirect(set, basis);
						if (!success) {
							direct.set(false);
							do {
								set = queue.take();
							} while (set != POISON_PILL);
							break;
						}
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}

		// Placer les jobs dans la queue
		for (ISet set : powerSet) {
			queue.put(set);
		}

		// Envoyer les pilules empoisonnées
		for (int i = 0; i < nbThreads; i++) {
			queue.put(POISON_PILL);
		}

		// Attendre la fin
		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

		return direct.get();
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
				if (++count > 1)
					return false;
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
		boolean derivable = true;
//int count=0;
		for (Implication dep : subBasis) {
			ISet closure = computeClosure(dep.getPremise(), basis);
			ISet minimalConclusion = dep.getConclusion().newDifference(closure);
			if (!isDerivable(minimalConclusion, dep.getPremise(), basis)) {
				derivable = false;
//				count++;
				break;
			}
		}
//			if(count>0)	System.out.println("Not derivable:"+count);
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
			int support = implication.getSupportSize();
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

	public static List<ISet> generatePowerSet(int N, ISetFactory factory) {
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

	// Génère la liste de tous les sous-ensembles de {0,..,N-1} de taille <=
	// maxCardinality
	public static List<ISet> generatePowerSet(int N, int maxCardinality, ISetFactory factory) {
		List<ISet> result = new ArrayList<>();
		if (N <= 0) {
			// univers vide -> seul l'ensemble vide
			ISet empty = factory.createSet();
			result.add(empty);
			return result;
		}
		if (maxCardinality < 0)
			return result;
		if (maxCardinality > N)
			maxCardinality = N;

		// ajouter l'ensemble vide
		result.add(factory.createSet());

		for (int k = 1; k <= maxCardinality; k++) {
			generateCombinationsIterative(N, k, factory, result);
		}
		return result;
	}

	private static void generateCombinationsIterative(int N, int k, ISetFactory factory, List<ISet> result) {
		if (k == 0) {
			result.add(factory.createSet());
			return;
		}
		// première combinaison lexicographique : [0,1,2,...,k-1]
		int[] comb = new int[k];
		for (int i = 0; i < k; i++)
			comb[i] = i;

		while (true) {
			// construire l'ISet correspondant (nouvelle instance à chaque itération)
			ISet s = factory.createSet();
			for (int i = 0; i < k; i++)
				s.add(comb[i]);
			result.add(s);

			// générer la combinaison suivante
			int i;
			for (i = k - 1; i >= 0; i--) {
				if (comb[i] < N - k + i) { // on peut incrémenter à la position i
					comb[i]++;
					for (int j = i + 1; j < k; j++)
						comb[j] = comb[j - 1] + 1;
					break;
				}
			}
			if (i < 0)
				break; // plus de combinaison possible
		}
	}

	private static List<ISet> generatePowerSet2(int N, int maxCardinality, ISetFactory factory) {
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
		org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector<Implication, DefaultEdge> sci = new GabowStrongConnectivityInspector<>(
				graph);
		List<Set<Implication>> stronglyConnectedSets = sci.stronglyConnectedSets();

		// Affichage
		for (Set<Implication> component : stronglyConnectedSets) {
			System.out.println("connected set : " + component.size());
		}
		return new TopologicalOrderIterator<Implication, DefaultEdge>(graph);
	}
    public static List<ISet> findDCycles(
            List<Implication> dBasis) {

        Graph<Integer, DefaultEdge> dGraph =
                new SimpleDirectedGraph<>(DefaultEdge.class);

        // Build D-relation graph
        for (Implication impl : dBasis) {

            // binary implications DO NOT generate D-relation
            if (impl.getPremise().cardinality() <= 1)
                continue;

            int x = impl.getConclusion().iterator().next();
            dGraph.addVertex(x);

            for (Iterator<Integer> it = impl.getPremise().iterator(); it.hasNext();) {
                int y = it.next();
                dGraph.addVertex(y);

                // xDy : edge x -> y
                dGraph.addEdge(x, y);
            }
        }

        // Strongly connected components
        GabowStrongConnectivityInspector<Integer, DefaultEdge> scc =
                new GabowStrongConnectivityInspector<>(dGraph);

        List<ISet> cycles = new ArrayList<>();
		BitSetFactory factory = new BitSetFactory();

        for (Set<Integer> component : scc.stronglyConnectedSets()) {
            if (component.size() > 1) {
                ISet cycle = factory.createSet();
                for (int v : component)
                    cycle.add(v);
                cycles.add(cycle);
            }
        }

        return cycles;
    }
    
	public static List<ISet> findDCycles2(List<Implication> basis) {
		SimpleDirectedGraph<Integer, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
		for (Implication impl : basis) {
			if (impl.getPremise().cardinality() > 1) {
				for (Iterator<Integer> itConclusion = impl.getConclusion().iterator(); itConclusion.hasNext();) {
					int conclusion = itConclusion.next();
					graph.addVertex(conclusion);
					for (Iterator<Integer> itPremise = impl.getPremise().iterator(); itPremise.hasNext();) {
						int premise = itPremise.next();
						graph.addVertex(premise);
						graph.addEdge(premise,conclusion);
					}
				}
			}
		}
		org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector<Integer, DefaultEdge> sci = new GabowStrongConnectivityInspector<>(
				graph);
		List<Set<Integer>> stronglyConnectedSets = sci.stronglyConnectedSets();
		ArrayList<ISet> cycles = new ArrayList<>();
		BitSetFactory factory = new BitSetFactory();
		for (Set<Integer> set : stronglyConnectedSets) {
			if (set.size() > 1) {
				ISet iset = factory.createSet();
				for (int vertex : set) {
					iset.add(vertex);
				}
				cycles.add(iset);
			}
		}
		return cycles;
	}

}
