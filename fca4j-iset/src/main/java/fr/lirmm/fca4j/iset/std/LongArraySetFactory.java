/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating growable sets backed by a packed array of 64-bit words
 * (a bitset stored as {@code long[]}).
 *
 * <p>
 * Unlike {@code java.util.BitSet}, the backing words are reachable, so the
 * hot-loop primitives are word-level, allocation-free and early-exiting:
 * <ul>
 * <li>{@link SetWithLongArray#containsAll} tests inclusion as
 * {@code (other[w] & ~this[w]) == 0} over the words, returning at the first
 * miss, with no clone/or/cardinality;</li>
 * <li>{@link SetWithLongArray#cardinality} is a sum of {@code Long.bitCount};</li>
 * <li>AND / ANDNOT / OR / subset never materialise an intermediate set for the
 * test itself.</li>
 * </ul>
 * This is decisive on small dense universes, where roaring's 65536-wide
 * containers and {@code java.util.BitSet}'s clone-based {@code containsAll} both
 * lose to a handful of word operations. ROARING_BITMAP / INT_ARRAY remain the
 * better choice for large or sparse universes.
 *
 * <p>
 * The set grows on demand, like BITSET and ROARING_BITMAP; {@link #fixedSize()}
 * is {@code false}. Trailing all-zero words are harmless (they contribute
 * nothing to cardinality, iteration, equals or hashCode), so no wordsInUse
 * bookkeeping is needed.
 *
 * @author agutierr
 */
public class LongArraySetFactory extends AbstractSetFactory {

	public LongArraySetFactory() {
	}

	/** Number of 64-bit words needed to address {@code bits} bits (at least 1). */
	private static int wordsFor(int bits) {
		int n = (bits + 63) >> 6;
		return n < 1 ? 1 : n;
	}

	@Override
	public ISet createSet() {
		return new SetWithLongArray();
	}

	@Override
	public ISet createSet(BitSet bs) {
		return new SetWithLongArray(bs);
	}

	@Override
	public ISet createSet(int initialCapacity) {
		return new SetWithLongArray(initialCapacity);
	}

	@Override
	public boolean ordered() {
		return true;
	}

	@Override
	public boolean fixedSize() {
		return false;
	}

	@Override
	public String name() {
		return "BITSET_PACKED";
	}

	@Override
	public ISet clone(ISet b) {
		long[] w = ((SetWithLongArray) b).words;
		return new SetWithLongArray(Arrays.copyOf(w, w.length));
	}

	/**
	 * The Class SetWithLongArray.
	 */
	public class SetWithLongArray extends AbstractOrderedSet {

		/** words[w] holds bits [64w, 64w+63]; bit i set iff (words[i&gt;&gt;6] &amp; (1L &lt;&lt; i)) != 0. */
		private long[] words;

		SetWithLongArray() {
			words = new long[1];
		}

		SetWithLongArray(int capacityInBits) {
			words = new long[wordsFor(capacityInBits)];
		}

		SetWithLongArray(long[] words) {
			this.words = (words.length == 0) ? new long[1] : words;
		}

		SetWithLongArray(BitSet bs) {
			long[] src = bs.toLongArray();
			words = (src.length == 0) ? new long[1] : src;
		}

		/** Ensure the backing array can address word index {@code w}. */
		private void ensure(int w) {
			if (w >= words.length) {
				int newLen = words.length << 1;
				if (newLen <= w) {
					newLen = w + 1;
				}
				words = Arrays.copyOf(words, newLen);
			}
		}

		@Override
		public void add(int num) {
			int w = num >> 6;
			ensure(w);
			words[w] |= 1L << num; // Java masks the shift amount to its low 6 bits
		}

		@Override
		public void addAll(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			ensure(o.length - 1);
			for (int w = 0; w < o.length; w++) {
				words[w] |= o[w];
			}
		}

		@Override
		public void setTo(ISet other) {
			if (other == this) {
				return;
			}
			long[] o = ((SetWithLongArray) other).words;
			if (words.length < o.length) {
				words = Arrays.copyOf(o, o.length);
			} else {
				System.arraycopy(o, 0, words, 0, o.length);
				Arrays.fill(words, o.length, words.length, 0L);
			}
		}

		@Override
		public boolean contains(int num) {
			int w = num >> 6;
			return w < words.length && (words[w] & (1L << num)) != 0L;
		}

		/** Inclusion test: {@code anotherSet} subseteq {@code this}, no allocation. */
		@Override
		public boolean containsAll(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			int ol = o.length;
			int tl = words.length;
			for (int w = 0; w < ol; w++) {
				long tw = (w < tl) ? words[w] : 0L;
				if ((o[w] & ~tw) != 0L) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int capacity() {
			return words.length << 6;
		}

		@Override
		public int cardinality() {
			int c = 0;
			for (int w = 0; w < words.length; w++) {
				c += Long.bitCount(words[w]);
			}
			return c;
		}

		@Override
		public void fill(int size) {
			if (size <= 0) {
				return;
			}
			int lastW = (size - 1) >> 6;
			ensure(lastW);
			for (int w = 0; w < lastW; w++) {
				words[w] = ~0L;
			}
			int rem = size & 63;
			words[lastW] |= (rem == 0) ? ~0L : ((1L << rem) - 1L);
		}

		@Override
		public void clear(int size) {
			if (size <= 0) {
				return;
			}
			int lastW = (size - 1) >> 6;
			if (lastW >= words.length) {
				lastW = words.length - 1;
				for (int w = 0; w <= lastW; w++) {
					words[w] = 0L;
				}
				return;
			}
			for (int w = 0; w < lastW; w++) {
				words[w] = 0L;
			}
			int rem = size & 63;
			long mask = (rem == 0) ? ~0L : ((1L << rem) - 1L);
			words[lastW] &= ~mask;
		}

		@Override
		public void remove(int num) {
			int w = num >> 6;
			if (w < words.length) {
				words[w] &= ~(1L << num);
			}
		}

		@Override
		public void removeAll(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			int n = Math.min(words.length, o.length);
			for (int w = 0; w < n; w++) {
				words[w] &= ~o[w];
			}
		}

		@Override
		public void retainAll(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			int ol = o.length;
			for (int w = 0; w < words.length; w++) {
				words[w] &= (w < ol) ? o[w] : 0L;
			}
		}

		@Override
		public ISet newIntersect(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			int n = Math.min(words.length, o.length);
			long[] r = new long[n < 1 ? 1 : n];
			for (int w = 0; w < n; w++) {
				r[w] = words[w] & o[w];
			}
			return new SetWithLongArray(r);
		}

		@Override
		public ISet newDifference(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			long[] r = Arrays.copyOf(words, words.length);
			int n = Math.min(r.length, o.length);
			for (int w = 0; w < n; w++) {
				r[w] &= ~o[w];
			}
			return new SetWithLongArray(r);
		}

		@Override
		public boolean intersects(ISet anotherSet) {
			long[] o = ((SetWithLongArray) anotherSet).words;
			int n = Math.min(words.length, o.length);
			for (int w = 0; w < n; w++) {
				if ((words[w] & o[w]) != 0L) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean isEmpty() {
			for (int w = 0; w < words.length; w++) {
				if (words[w] != 0L) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int first() {
			for (int w = 0; w < words.length; w++) {
				long word = words[w];
				if (word != 0L) {
					return (w << 6) + Long.numberOfTrailingZeros(word);
				}
			}
			return -1;
		}

		@Override
		public int last() {
			for (int w = words.length - 1; w >= 0; w--) {
				long word = words[w];
				if (word != 0L) {
					return (w << 6) + 63 - Long.numberOfLeadingZeros(word);
				}
			}
			return -1;
		}

		@Override
		public Iterator<Integer> iterator() {
			return new Iterator<Integer>() {
				private int w = 0;
				private long cur = words.length > 0 ? words[0] : 0L;

				@Override
				public boolean hasNext() {
					while (cur == 0L) {
						if (++w >= words.length) {
							return false;
						}
						cur = words[w];
					}
					return true;
				}

				@Override
				public Integer next() {
					if (!hasNext()) {
						throw new NoSuchElementException();
					}
					int bit = Long.numberOfTrailingZeros(cur);
					cur &= cur - 1; // clear lowest set bit
					return (w << 6) + bit;
				}
			};
		}

		@Override
		public int hashCode() {
			long h = 1234L;
			for (int w = words.length - 1; w >= 0; w--) {
				h ^= words[w] * (w + 1);
			}
			return (int) ((h >> 32) ^ h);
		}

		@Override
		public boolean equals(Object aSet) {
			try {
				long[] o = ((SetWithLongArray) aSet).words;
				int max = Math.max(words.length, o.length);
				for (int w = 0; w < max; w++) {
					long tw = (w < words.length) ? words[w] : 0L;
					long ow = (w < o.length) ? o[w] : 0L;
					if (tw != ow) {
						return false;
					}
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}
}
