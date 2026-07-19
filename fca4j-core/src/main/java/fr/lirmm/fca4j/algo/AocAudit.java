/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * AOC-poset audit harness, parameterised by algorithm.
 *
 * <p>For every context, the algorithm under test is run and checked three ways:
 * <ol>
 * <li><b>order vs inclusion</b> — the extent determines the concept, so for two
 * distinct concepts u, v: {@code v reachable from u <=> extent(u) strictly
 * included in extent(v)}. Catches a missing edge or a spurious relation.</li>
 * <li><b>transitive reduction</b> — no edge u -&gt; v doubled by a path of length &gt;= 2.
 * This is meaningful only for algorithms that claim to emit a reduced diagram
 * directly (Ares with SKIP_REDUCE, Pluton, Ceres); Hermes is the oracle and is
 * assumed correct.</li>
 * <li><b>cross-check with Hermes</b> — same concepts (keyed by reduced extent and
 * reduced intent) and same edges.</li>
 * </ol>
 *
 * <p>Two phases: an exhaustive sweep of all small contexts (minimal witness by
 * construction), then a random phase whose shrinker keeps the failure <i>kind</i>
 * constant. One minimal witness per (algorithm, kind) is printed at the end.
 *
 * <p>Usage: {@code AocAudit [algo] [seed] [rounds]} where algo is one of
 * {@code ares, pluton, ceres, all} (default {@code all}).
 */
public class AocAudit {

    private static final int SWEEP_MAX_CELLS = 12;
    private static final int RANDOM_MAX_OBJECTS = 7;
    private static final int RANDOM_MAX_ATTRIBUTES = 7;

    // ---- algorithm registry ------------------------------------------------

    private interface AlgoFactory {
        IConceptOrder build(IBinaryContext ctx);
    }

    private static IConceptOrder runAres(IBinaryContext ctx) {
        AOC_poset_Ares a = new AOC_poset_Ares(ctx, new Chrono("chrono_ares"));
        a.run();
        return a.getResult();
    }

    private static IConceptOrder runPluton(IBinaryContext ctx) {
        AOC_poset_Pluton a = new AOC_poset_Pluton(ctx, new Chrono("chrono_pluton"));
        a.run();
        return a.getResult();
    }

    private static IConceptOrder runCeres(IBinaryContext ctx) {
        AOC_poset_Ceres a = new AOC_poset_Ceres(ctx, new Chrono("chrono_ceres"));
        a.run();
        return a.getResult();
    }

    private static IConceptOrder runHermes(IBinaryContext ctx) {
        AOC_poset_Hermes a = new AOC_poset_Hermes(ctx, new Chrono("chrono_hermes"));
        a.run();
        return a.getResult();
    }

    // ---- failure model -----------------------------------------------------

    private static final class Failure {
        final String kind;
        final String detail;
        final Throwable cause;

        Failure(String kind, String detail, Throwable cause) {
            this.kind = kind;
            this.detail = detail;
            this.cause = cause;
        }
    }

    private static Failure crash(String who, Throwable t) {
        String frame = "?";
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("fr.lirmm.fca4j")) {
                frame = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1)
                        + "." + e.getMethodName() + ":" + e.getLineNumber();
                break;
            }
        }
        return new Failure(who + " threw " + t.getClass().getSimpleName() + " at " + frame,
                who + " threw " + t, t);
    }

    // ---- entry point -------------------------------------------------------

    public static void main(String[] args) {
        String which = args.length > 0 ? args[0].toLowerCase() : "all";
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260709L;
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 20000;

        Map<String, AlgoFactory> algos = new LinkedHashMap<>();
        if (which.equals("ares") || which.equals("all")) {
            algos.put("Ares", AocAudit::runAres);
        }
        if (which.equals("pluton") || which.equals("all")) {
            algos.put("Pluton", AocAudit::runPluton);
        }
        if (which.equals("ceres") || which.equals("all")) {
            algos.put("Ceres", AocAudit::runCeres);
        }

        for (Map.Entry<String, AlgoFactory> e : algos.entrySet()) {
            audit(e.getKey(), e.getValue(), seed, rounds);
        }
    }

    private static void audit(String name, AlgoFactory algo, long seed, int rounds) {
        System.out.println("###################  " + name + "  ###################");
        Map<String, boolean[][]> witnesses = new LinkedHashMap<>();
        Map<String, String> details = new LinkedHashMap<>();

        System.out.println("--- exhaustive sweep (contexts of at most " + SWEEP_MAX_CELLS + " cells) ---");
        int swept = 0;
        int clean = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int nbObj = 1; nbObj <= SWEEP_MAX_CELLS; nbObj++) {
            for (int nbAttr = 1; nbObj * nbAttr <= SWEEP_MAX_CELLS; nbAttr++) {
                int cells = nbObj * nbAttr;
                for (long mask = 0; mask < (1L << cells); mask++) {
                    boolean[][] cross = decode(mask, nbObj, nbAttr);
                    swept++;
                    Failure f = check(algo, cross);
                    if (f == null) {
                        clean++;
                    } else {
                        counts.merge(f.kind, 1, Integer::sum);
                        if (!witnesses.containsKey(f.kind)) {
                            witnesses.put(f.kind, cross);
                            details.put(f.kind, f.detail);
                            if (f.cause != null) {
                                System.out.println("first stack trace for kind: " + f.kind);
                                f.cause.printStackTrace(System.out);
                            }
                        }
                    }
                }
            }
        }
        System.out.println(swept + " swept, " + clean + " clean");
        counts.forEach((k, v) -> System.out.println("  " + v + " x " + k));

        System.out.println("--- random phase (seed " + seed + ", " + rounds + " rounds) ---");
        Random rnd = new Random(seed);
        for (int round = 0; round < rounds; round++) {
            int nbObj = 1 + rnd.nextInt(RANDOM_MAX_OBJECTS);
            int nbAttr = 1 + rnd.nextInt(RANDOM_MAX_ATTRIBUTES);
            double density = 0.15 + 0.7 * rnd.nextDouble();
            boolean[][] cross = new boolean[nbObj][nbAttr];
            for (int o = 0; o < nbObj; o++) {
                for (int a = 0; a < nbAttr; a++) {
                    cross[o][a] = rnd.nextDouble() < density;
                }
            }
            Failure f = check(algo, cross);
            if (f == null || witnesses.containsKey(f.kind)) {
                continue;
            }
            System.out.println("new kind at round " + round + ": " + f.kind);
            boolean[][] minimal = shrink(algo, cross, f.kind);
            Failure mf = check(algo, minimal);
            witnesses.put(f.kind, minimal);
            details.put(f.kind, mf != null ? mf.detail : f.detail);
        }

        if (witnesses.isEmpty()) {
            System.out.println("=> no discrepancy for " + name);
        } else {
            for (Map.Entry<String, boolean[][]> e : witnesses.entrySet()) {
                System.out.println("=== " + name + " / " + e.getKey() + " ===");
                System.out.println(details.get(e.getKey()));
                print(e.getValue());
            }
        }
        System.out.println();
    }

    // ---- the three checks --------------------------------------------------

    private static Failure check(AlgoFactory algo, boolean[][] cross) {
        IConceptOrder co;
        IConceptOrder oracle;
        try {
            co = algo.build(build(cross));
        } catch (RuntimeException e) {
            return crash("algo", e);
        }
        try {
            oracle = runHermes(build(cross));
        } catch (RuntimeException e) {
            return crash("Hermes", e);
        }
        if (co == null || oracle == null) {
            return null; // degenerate context, nothing to compare
        }
        try {
            Failure f = checkOrderMatchesInclusion(co);
            if (f != null) {
                return f;
            }
            f = checkTransitivelyReduced(co);
            if (f != null) {
                return f;
            }
            return compare(co, oracle);
        } catch (RuntimeException e) {
            return crash("audit", e);
        }
    }

    private static Failure checkOrderMatchesInclusion(IConceptOrder co) {
        List<Integer> ids = new ArrayList<>(co.getConcepts());
        Map<Integer, Set<Integer>> up = new HashMap<>();
        for (int c : ids) {
            up.put(c, ancestors(co, c));
        }
        for (int u : ids) {
            ISet eu = co.getConceptExtent(u);
            for (int v : ids) {
                if (u == v) {
                    continue;
                }
                ISet ev = co.getConceptExtent(v);
                if (eu.equals(ev)) {
                    return new Failure("duplicate extent",
                            "concepts " + u + " and " + v + " share the extent " + sorted(eu), null);
                }
                boolean included = ev.containsAll(eu);
                boolean reachable = up.get(u).contains(v);
                if (included && !reachable) {
                    return new Failure("missing order relation",
                            "extent(" + u + ")=" + sorted(eu) + " included in extent(" + v + ")="
                                    + sorted(ev) + " but " + v + " unreachable from " + u, null);
                }
                if (!included && reachable) {
                    return new Failure("spurious order relation",
                            v + " reachable from " + u + " but extent(" + u + ")=" + sorted(eu)
                                    + " not included in extent(" + v + ")=" + sorted(ev), null);
                }
            }
        }
        return null;
    }

    private static Failure checkTransitivelyReduced(IConceptOrder co) {
        for (int u : co.getConcepts()) {
            List<Integer> succ = new ArrayList<>();
            for (Iterator<Integer> it = co.getUpperCoverIterator(u); it.hasNext();) {
                succ.add(it.next());
            }
            if (succ.size() < 2) {
                continue;
            }
            Set<Integer> viaLongPath = new HashSet<>();
            Deque<Integer> stack = new ArrayDeque<>();
            for (int s : succ) {
                for (Iterator<Integer> it = co.getUpperCoverIterator(s); it.hasNext();) {
                    stack.push(it.next());
                }
            }
            while (!stack.isEmpty()) {
                int w = stack.pop();
                if (!viaLongPath.add(w)) {
                    continue;
                }
                for (Iterator<Integer> it = co.getUpperCoverIterator(w); it.hasNext();) {
                    stack.push(it.next());
                }
            }
            for (int v : succ) {
                if (viaLongPath.contains(v)) {
                    return new Failure("transitive edge",
                            "edge " + u + " -> " + v + " is also reachable by a longer path", null);
                }
            }
        }
        return null;
    }

    private static Failure compare(IConceptOrder co, IConceptOrder oracle) {
        Map<Integer, String> ka = keys(co);
        Map<Integer, String> kh = keys(oracle);
        Set<String> ca = new TreeSet<>(ka.values());
        Set<String> ch = new TreeSet<>(kh.values());
        if (!ca.equals(ch)) {
            Set<String> onlyAlgo = new TreeSet<>(ca);
            onlyAlgo.removeAll(ch);
            Set<String> onlyHermes = new TreeSet<>(ch);
            onlyHermes.removeAll(ca);
            return new Failure("concept sets differ",
                    "only in algo: " + onlyAlgo + " ; only in Hermes: " + onlyHermes, null);
        }
        Set<String> ea = edges(co, ka);
        Set<String> eh = edges(oracle, kh);
        if (!ea.equals(eh)) {
            Set<String> onlyAlgo = new TreeSet<>(ea);
            onlyAlgo.removeAll(eh);
            Set<String> onlyHermes = new TreeSet<>(eh);
            onlyHermes.removeAll(ea);
            return new Failure("edge sets differ",
                    "only in algo: " + onlyAlgo + " ; only in Hermes: " + onlyHermes, null);
        }
        return null;
    }

    // ---- plumbing ----------------------------------------------------------

    private static Set<Integer> ancestors(IConceptOrder co, int start) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            if (!seen.add(v)) {
                continue;
            }
            for (Iterator<Integer> it = co.getUpperCoverIterator(v); it.hasNext();) {
                stack.push(it.next());
            }
        }
        seen.remove(start);
        return seen;
    }

    private static Map<Integer, String> keys(IConceptOrder co) {
        Map<Integer, String> m = new HashMap<>();
        for (int c : co.getConcepts()) {
            m.put(c, "O" + sorted(co.getConceptReducedExtent(c)) + "/A" + sorted(co.getConceptReducedIntent(c)));
        }
        return m;
    }

    private static Set<String> edges(IConceptOrder co, Map<Integer, String> k) {
        Set<String> res = new HashSet<>();
        for (int u : co.getConcepts()) {
            for (Iterator<Integer> it = co.getUpperCoverIterator(u); it.hasNext();) {
                res.add(k.get(u) + " -> " + k.get(it.next()));
            }
        }
        return res;
    }

    private static String sorted(ISet s) {
        TreeSet<Integer> t = new TreeSet<>();
        for (Iterator<Integer> it = s.iterator(); it.hasNext();) {
            t.add(it.next());
        }
        return t.toString();
    }

    private static IBinaryContext build(boolean[][] cross) {
        int nbObj = cross.length;
        int nbAttr = cross[0].length;
        IBinaryContext ctx = new BinaryContext(nbObj, nbAttr, "audit", new BitSetFactory());
        for (int o = 0; o < nbObj; o++) {
            ctx.addObjectName("o" + o);
        }
        for (int a = 0; a < nbAttr; a++) {
            ctx.addAttributeName("a" + a);
        }
        for (int o = 0; o < nbObj; o++) {
            for (int a = 0; a < nbAttr; a++) {
                if (cross[o][a]) {
                    ctx.set(o, a, true);
                }
            }
        }
        return ctx;
    }

    private static boolean[][] decode(long mask, int nbObj, int nbAttr) {
        boolean[][] cross = new boolean[nbObj][nbAttr];
        int bit = 0;
        for (int o = 0; o < nbObj; o++) {
            for (int a = 0; a < nbAttr; a++, bit++) {
                cross[o][a] = ((mask >>> bit) & 1L) != 0;
            }
        }
        return cross;
    }

    // ---- kind-preserving greedy shrinker -----------------------------------

    private static boolean stillFails(AlgoFactory algo, boolean[][] cross, String kind) {
        Failure f = check(algo, cross);
        return f != null && f.kind.equals(kind);
    }

    private static boolean[][] shrink(AlgoFactory algo, boolean[][] cross, String kind) {
        boolean[][] best = cross;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int o = 0; o < best.length && best.length > 1; o++) {
                boolean[][] candidate = dropObject(best, o);
                if (stillFails(algo, candidate, kind)) {
                    best = candidate;
                    progress = true;
                    break;
                }
            }
            if (progress) {
                continue;
            }
            for (int a = 0; a < best[0].length && best[0].length > 1; a++) {
                boolean[][] candidate = dropAttribute(best, a);
                if (stillFails(algo, candidate, kind)) {
                    best = candidate;
                    progress = true;
                    break;
                }
            }
            if (progress) {
                continue;
            }
            outer:
            for (int o = 0; o < best.length; o++) {
                for (int a = 0; a < best[0].length; a++) {
                    if (!best[o][a]) {
                        continue;
                    }
                    boolean[][] candidate = copy(best);
                    candidate[o][a] = false;
                    if (stillFails(algo, candidate, kind)) {
                        best = candidate;
                        progress = true;
                        break outer;
                    }
                }
            }
        }
        return best;
    }

    private static boolean[][] copy(boolean[][] m) {
        boolean[][] r = new boolean[m.length][m[0].length];
        for (int i = 0; i < m.length; i++) {
            System.arraycopy(m[i], 0, r[i], 0, m[0].length);
        }
        return r;
    }

    private static boolean[][] dropObject(boolean[][] m, int o) {
        boolean[][] r = new boolean[m.length - 1][m[0].length];
        for (int i = 0, j = 0; i < m.length; i++) {
            if (i != o) {
                System.arraycopy(m[i], 0, r[j++], 0, m[0].length);
            }
        }
        return r;
    }

    private static boolean[][] dropAttribute(boolean[][] m, int a) {
        boolean[][] r = new boolean[m.length][m[0].length - 1];
        for (int i = 0; i < m.length; i++) {
            for (int k = 0, j = 0; k < m[0].length; k++) {
                if (k != a) {
                    r[i][j++] = m[i][k];
                }
            }
        }
        return r;
    }

    private static void print(boolean[][] m) {
        StringBuilder sb = new StringBuilder("   ");
        for (int a = 0; a < m[0].length; a++) {
            sb.append(' ').append((char) ('a' + a));
        }
        System.out.println(sb);
        for (int o = 0; o < m.length; o++) {
            sb = new StringBuilder(" " + o + " ");
            for (int a = 0; a < m[0].length; a++) {
                sb.append(' ').append(m[o][a] ? 'x' : '.');
            }
            System.out.println(sb);
        }
    }
}
