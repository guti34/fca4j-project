package fr.lirmm.fca4j.util;

import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.RuleUtilities;

import java.util.*;

/**
 * MinGen / MinGen0 utility (CLA'12)
  */
public class MinGenUtils {

    private final ISetFactory factory;

    public MinGenUtils(ISetFactory factory) {
        this.factory = factory;
    }

    /* ============================================================
     *              Minimal elements (antichain)
     * ============================================================ */
    private Set<ISet> minimalElements(Collection<ISet> coll) {

        List<ISet> list = new ArrayList<>();
        for (ISet s : coll) list.add(s.clone());

        Set<ISet> res = new HashSet<>();

        for (ISet x : list) {
            boolean removable = false;
            for (ISet y : list) {
                if (x == y) continue;
                if (!x.equals(y) && x.containsAll(y)) {
                    removable = true;
                    break;
                }
            }
            if (!removable) res.add(x);
        }
        return res;
    }

    /* ============================================================
     *                           CLS(A, Γ)
     * ============================================================ */
    private static class ClsResult {
        ISet closure;
        List<Implication> gammaPrime;
        ClsResult(ISet c, List<Implication> g) { closure = c; gammaPrime = g; }
    }

    private ClsResult cls(ISet A, List<Implication> Gamma) {

        ISet Aplus = RuleUtilities.computeClosure(A, Gamma);

        List<Implication> Gp = new ArrayList<>();

        for (Implication imp : Gamma) {
            ISet B = imp.getPremise();
            ISet C = imp.getConclusion();

            // Eq.I : absorbée
            if (Aplus.containsAll(B)) continue;

            // Eq.II : inutile
            if (Aplus.containsAll(C)) continue;

            // Eq.III : réduction
            ISet newLeft  = B.newDifference(A);
            ISet newRight = C.newDifference(Aplus);

            if (!newLeft.isEmpty() || !newRight.isEmpty())
                Gp.add(new Implication(newLeft, newRight,imp.getSupport()));
        }

//        RuleUtilities.simplify(Gp);

        return new ClsResult(Aplus, Gp);
    }

    /* ============================================================
     *                         TRV (MinGen)
     * ============================================================ */
    private Map<ISet, Set<ISet>> trv(ISet M, List<Implication> Gamma) {

        ISet blocked = factory.createSet();
        for (Implication imp : Gamma) {
            blocked.addAll(imp.getPremise());
        }

        ISet free = M.newDifference(blocked);

        // Convert free -> List<Integer>
        List<Integer> attrs = new ArrayList<Integer>();
        Iterator<Integer> itFree = free.iterator();
        while (itFree.hasNext()) {
            attrs.add(itFree.next());
        }

        Map<ISet, Set<ISet>> res = new HashMap<ISet, Set<ISet>>();

        int n = attrs.size();
        int max = 1 << n;

        for (int mask = 0; mask < max; mask++) {

            ISet X = factory.createSet();

            // Construire X depuis les bits du mask
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    X.add(attrs.get(i));
                }
            }

            boolean ok = true;
            for (Implication imp : Gamma) {
                if (X.containsAll(imp.getPremise())) {
                    ok = false;
                    break;
                }
            }

            if (!ok) continue;

            // Insert dans res (version Java 8 sans computeIfAbsent)
            Set<ISet> gens = res.get(X);
            if (gens == null) {
                gens = new HashSet<ISet>();
                res.put(X, gens);
            }

            gens.add(X.clone());
        }

        return res;
    }
    /* ============================================================
     *                       TRV0 (MinGen0)
     * ============================================================ */
    private Map<ISet, Set<ISet>> trv0() {
        Map<ISet, Set<ISet>> res = new HashMap<>();
        ISet empty = factory.createSet();
        Set<ISet> gens = new HashSet<>();
        gens.add(factory.createSet()); // {∅}
        res.put(empty, gens);
        return res;
    }

    /* ============================================================
     *                     Add(<C,{D}>, Phi)
     * ============================================================ */
    private Map<ISet, Set<ISet>> addPair(ISet C, ISet D, Map<ISet, Set<ISet>> Phi) {

        Map<ISet, Set<ISet>> out = new HashMap<>();

        for (Map.Entry<ISet, Set<ISet>> e : Phi.entrySet()) {

            ISet A = e.getKey();
            ISet newIntent = A.clone();
            newIntent.addAll(C);

            Set<ISet> gens = out.get(newIntent);
            if (gens == null) {
                gens = new HashSet<>();
                out.put(newIntent, gens);
            }

            for (ISet X : e.getValue()) {
                ISet g = X.clone();
                g.addAll(D);
                gens.add(g);
            }
        }

        // minimisation
        Map<ISet, Set<ISet>> res = new HashMap<>();
        for (Map.Entry<ISet, Set<ISet>> e : out.entrySet()) {
            Set<ISet> mins = minimalElements(e.getValue());
            res.put(e.getKey(), mins);
        }

        return res;
    }

    /* ============================================================
     *                         Join(Phi, Psi)
     * ============================================================ */
    private Map<ISet, Set<ISet>> join(Map<ISet, Set<ISet>> A, Map<ISet, Set<ISet>> B) {

        Map<ISet, Set<ISet>> out = new HashMap<>();

        // Copier A dans out
        for (Map.Entry<ISet, Set<ISet>> e : A.entrySet()) {
            out.put(e.getKey(), new HashSet<ISet>(e.getValue()));
        }

        // Fusionner B dans out
        for (Map.Entry<ISet, Set<ISet>> e : B.entrySet()) {

            ISet key = e.getKey();
            Set<ISet> vals = e.getValue();

            Set<ISet> existing = out.get(key);
            if (existing == null) {
                existing = new HashSet<ISet>();
                out.put(key, existing);
            }

            existing.addAll(vals);
            existing = minimalElements(existing);
            out.put(key, existing);
        }

        return out;
    }

    /* ============================================================
     *                          MinGen
     * ============================================================ */
    public Map<ISet, Set<ISet>> minGen(ISet M, List<Implication> Gamma) {

        if (Gamma.isEmpty())
            return trv(M, Gamma);

        Map<ISet, Set<ISet>> Phi = trv(M, Gamma);

        for (Implication imp : Gamma) {

            ISet A = imp.getPremise();

            ClsResult cr = cls(A, Gamma);
            ISet Aplus = cr.closure;
            List<Implication> Gp = cr.gammaPrime;

            ISet Mr = M.newDifference(Aplus);

            Map<ISet, Set<ISet>> rec = minGen(Mr, Gp);

            Map<ISet, Set<ISet>> added = addPair(Aplus, A, rec);

            Phi = join(Phi, added);
        }

        return Phi;
    }

    /* ============================================================
     *                         MinGen0
     * ============================================================ */
    public Map<ISet, Set<ISet>> minGen0(ISet M, List<Implication> Gamma) {

        if (Gamma.isEmpty())
            return trv0();

        Map<ISet, Set<ISet>> Phi = trv0();

        for (Implication imp : Gamma) {

            ISet A = imp.getPremise();

            ClsResult cr = cls(A, Gamma);
            ISet Aplus = cr.closure;
            List<Implication> Gp = cr.gammaPrime;

            ISet Mr = M.newDifference(Aplus);

            Map<ISet, Set<ISet>> rec = minGen0(Mr, Gp);

            Map<ISet, Set<ISet>> added = addPair(Aplus, A, rec);

            Phi = join(Phi, added);
        }

        return Phi;
    }
    /**
     * Compute all minimal generators for a single attribute.
     *
     * @param attribute the attribute index
     * @param Gamma the implication basis
     * @return a set of minimal generators (ISet)
     */
    public Set<ISet> getMinimalGenerators(int attribute, List<Implication> Gamma) {

        // M = {attribute}
        ISet M = factory.createSet();
        M.add(attribute);

        // Compute MinGen(M, Gamma)
        Map<ISet, Set<ISet>> res = minGen0(M, Gamma);

        // Union of all minimal generators in the output
        Set<ISet> gens = new HashSet<ISet>();

        for (Map.Entry<ISet, Set<ISet>> e : res.entrySet()) {
            for (ISet g : e.getValue()) {
                gens.add(g.clone());
            }
        }

        return gens;
    }}
