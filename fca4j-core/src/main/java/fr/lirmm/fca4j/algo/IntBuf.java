/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.Arrays;

/**
 * Tableau d'entiers primitifs à taille variable — remplace {@code
 * List<Integer>} dans les points chauds de {@link LinCbO} et
 * {@link LinCbOWithPruning} :
 * <ul>
 * <li>le compteur par implication ({@code counts}/{@code prevCount}), copié
 * à CHAQUE nœud de la récursion ({@code _LinClosureRC}) ;</li>
 * <li>{@code list}, le tableau par attribut des indices d'implications dont
 * la prémisse contient cet attribut, parcouru dans la boucle interne de
 * {@code _LinClosureRC} à chaque nœud de la récursion, pour chaque attribut
 * dépilé de {@code Z}.</li>
 * </ul>
 * Une {@code List<Integer>} autoboxe chaque valeur (un objet {@code Integer}
 * par élément) et sa copie ({@code new ArrayList<>(autre)}) ou son parcours
 * via {@code Iterator<Integer>} disperse ces objets sur le tas ; un
 * {@code int[]} rend la copie un simple {@code System.arraycopy} et le
 * parcours un simple accès indexé, sans allocation d'objet par élément —
 * même symptôme, en esprit, que le memcpy redondant de {@code prev_count}
 * déjà éliminé côté moteur C (lincbo_pruning.c).
 *
 * @author agutierr
 */
final class IntBuf {

	int[] data;
	int len;

	IntBuf(int capacity) {
		data = new int[capacity > 0 ? capacity : 4];
	}

	/** Copie indépendante — équivalent de {@code new ArrayList<>(other)}. */
	IntBuf(IntBuf other) {
		data = Arrays.copyOf(other.data, other.len);
		len = other.len;
	}

	int size() {
		return len;
	}

	int get(int i) {
		return data[i];
	}

	void set(int i, int v) {
		data[i] = v;
	}

	void add(int v) {
		if (len >= data.length) {
			// data.length peut valoir 0 (copie d'un IntBuf vide via
			// Arrays.copyOf(other.data, 0)) : doubler 0 reste 0 pour
			// toujours, d'où une ArrayIndexOutOfBoundsException si on ne
			// force pas une capacité minimale à chaque croissance.
			data = Arrays.copyOf(data, Math.max(data.length * 2, 4));
		}
		data[len++] = v;
	}
}
