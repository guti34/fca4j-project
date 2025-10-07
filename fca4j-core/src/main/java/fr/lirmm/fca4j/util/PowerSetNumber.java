package fr.lirmm.fca4j.util;

import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PowerSetNumber {
    private final TreeSet<Integer> exponents; // ex: [5,3,0] = 2^5 + 2^3 + 2^0

    public PowerSetNumber() { this.exponents = new TreeSet<>(); }
    public PowerSetNumber(Collection<Integer> exps) { this.exponents = new TreeSet<>(exps); }

    public static PowerSetNumber fromPowerOfTwo(int exponent) {
        PowerSetNumber n = new PowerSetNumber();
        n.exponents.add(exponent);
        return n;
    }

    public boolean isZero() { return exponents.isEmpty(); }

    /** Soustrait 2^k avec emprunt correct (corrigé) */
    public void subtractPowerOfTwo(int k) {
        if (isZero()) throw new IllegalStateException("Number is zero");

        // Si on a directement 2^k, on le supprime et on a fini
        if (exponents.remove(k)) return;

        // Cherche la plus petite puissance STRICTEMENT supérieure à k pour emprunter
        Integer higher = exponents.higher(k);
        if (higher == null) {
            throw new IllegalArgumentException("Cannot subtract 2^" + k + " from smaller number: " + this);
        }

        // On enlève 2^higher puis on remplace par la décomposition 2^k + 2^(k+1) + ... + 2^(higher-1)
        exponents.remove(higher);
        for (int i = k; i < higher; i++) {
            exponents.add(i); // idempotent : si déjà présent, reste présent
        }
        // IMPORTANT : ne rien supprimer ici — on veut garder 2^k (et les autres)
    }

    /** Soustrait un autre PowerSetNumber (vérifie d'abord que this >= other) */
    public void subtract(PowerSetNumber other) {
        if (this.compareTo(other) < 0) {
            throw new IllegalArgumentException("Subtraction would lead to negative result");
        }
        // on parcourt du plus grand exposant au plus petit pour limiter les emprunts inutiles
        for (int exp : other.exponents.descendingSet()) {
            subtractPowerOfTwo(exp);
        }
    }

    /** Compare lexicographiquement par exposants décroissants (this ? other) */
    public int compareTo(PowerSetNumber other) {
        Iterator<Integer> itA = this.exponents.descendingIterator();
        Iterator<Integer> itB = other.exponents.descendingIterator();
        while (itA.hasNext() && itB.hasNext()) {
            int a = itA.next();
            int b = itB.next();
            if (a != b) return Integer.compare(a, b);
        }
        if (itA.hasNext()) return 1;
        if (itB.hasNext()) return -1;
        return 0;
    }

    public BigDecimal toBigDecimal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int e : exponents) sum = sum.add(BigDecimal.valueOf(2).pow(e));
        return sum;
    }

    public double divide(PowerSetNumber other) {
        BigDecimal num = this.toBigDecimal();
        BigDecimal den = other.toBigDecimal();
        return num.divide(den, 30, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public String toString() {
        return "Σ2^" + exponents.descendingSet();
    }

    // Petit test
    public static void main(String[] args) {
        PowerSetNumber a = new PowerSetNumber(Arrays.asList(3,2,1,0));
        PowerSetNumber b = new PowerSetNumber(Arrays.asList(3,2,1,0));
        System.out.println("a = " + a);
        a.subtract(b);
        System.out.println("a - b = " + a + " (attendu : Σ2^[])");

        PowerSetNumber c = PowerSetNumber.fromPowerOfTwo(3); // 8
        c.subtractPowerOfTwo(0); // 8 - 1 = 7 => 2^2 + 2^1 + 2^0
        System.out.println("2^3 - 2^0 = " + c + " (attendu : Σ2^[2, 1, 0])");
    }
}
