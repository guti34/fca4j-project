/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.Iterator;

/**
 * The Class BoolArray.
 *
 * @author agutierr
 */
public class BoolArray implements Cloneable {

	/** The array. */
	boolean[] array;
	
	/** The last. */
	int last = -1;

	/**
	 * Instantiates a new bool array.
	 *
	 * @param size the size
	 */
	public BoolArray(int size) {
		array = new boolean[size];
	}

	/**
	 * Contains.
	 *
	 * @param num the num
	 * @return true, if successful
	 */
	public boolean contains(int num) {
		return num <= last && array[num];
	}

	/**
	 * Contains all.
	 *
	 * @param other the other
	 * @return true, if successful
	 */
	public boolean containsAll(BoolArray other) {
		for (int i = 0; i <= other.last; i++)
			if (other.array[i] && !array[i])
				return false;
		return true;
	}

	/**
	 * Size.
	 *
	 * @return the int
	 */
	public int size() {
		return array.length;
	}

	/**
	 * Cardinality.
	 *
	 * @return the int
	 */
	public int cardinality() {
		int count = 0;
		for (int i = 0; i <= last; i++)
			if (array[i])
				count++;
		return count;
	}

	/**
	 * Clear.
	 *
	 * @param size the size
	 */
	public void clear(int size) {
		for (int i = 0; i < size; i++)
			array[i] = false;
		if (last < size)
			last = -1;
	}

	/**
	 * Clear.
	 */
	public void clear() {
		for (int i = 0; i <= last; i++)
			array[i] = false;
		last = -1;
	}

	/**
	 * And.
	 *
	 * @param second the second
	 */
	public void and(BoolArray second) {
		int min_last = Integer.min(last, second.last);
		last = -1;
		for (int i = 0; i <= min_last; i++) {
			array[i] &= second.array[i];
			if (array[i])
				last = i;
		}
	}

	/**
	 * And not.
	 *
	 * @param set2 the set 2
	 */
	public void andNot(BoolArray set2) {
		boolean recalcLast = false;
		int min = Integer.min(last, set2.last);
		if(min<0) return;
		for (int i = min; i >= 0; i--)
			if (set2.array[i] && array[i]) {
				array[i] = false;
				if (i == last)
					recalcLast = true;
			}
		if (recalcLast){
			for (int i = last; i >= 0; i--)
				if (array[i]) {
					last = i;
					return;
				}
			last=-1;
		}
	}

	/**
	 * Adds the all.
	 *
	 * @param second the second
	 */
	public void addAll(BoolArray second) {
		for (int i = 0; i <= second.last; i++)
			array[i] |= second.array[i];
		if (second.last > last)
			last = second.last;
	}

	/**
	 * Adds the.
	 *
	 * @param num the num
	 */
	public void add(int num) {
		array[num] = true;
		if (num > last) {
			for (int i = last + 1; i < num; i++)
				array[i] = false;
			last = num;
		}
	}

	/**
	 * Removes the.
	 *
	 * @param num the num
	 */
	public void remove(int num) {
		array[num] = false;
		if (num == last) {
			for (int i = last - 1; i >= 0; i--)
				if (array[i]) {
					last = i;
					return;
				}
			last = -1;
		}
	}

	/**
	 * Fill.
	 *
	 * @param size the size
	 */
	public void fill(int size) {
		for (int i = 0; i < size; i++) {
			array[i] = true;
		}
		if (size - 1 > last)
			last = size - 1;
	}

	/**
	 * Iterator.
	 *
	 * @return the iterator
	 */
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>() {
			int counter = 0;

			@Override
			public boolean hasNext() {
				return counter <= last;
			}

			@Override
			public Integer next() {
				for (int i = counter;; i++)
					if (array[i]) {
						counter = i + 1;
						return i;
					}
			}
		};
	}

	/**
	 * First.
	 *
	 * @return the int
	 */
	int first() {
		for (int i = 0; i <= last; i++)
			if (array[i])
				return i;
		return -1;
	}

	/**
	 * Last.
	 *
	 * @return the int
	 */
	int last() {
		return last;
	}

	/**
	 * Checks if is empty.
	 *
	 * @return true, if is empty
	 */
	boolean isEmpty() {
		return last < 0;
	}

	/**
	 * Clone.
	 *
	 * @return the bool array
	 */
	@Override
	public BoolArray clone() {
		BoolArray clone = new BoolArray(array.length);
		clone.last = last;
		System.arraycopy(array, 0, clone.array, 0, array.length);
		return clone;
	}
}
