package fr.lirmm.fca4j.iset.gpu;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/* le crible d'hératosthène */
class GPUSetFactoryTest {

	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testBitSet1() {
		test1(10000,new BitSetFactory());
	}
	@Test
	void testGPUSet1() {
		test1(10000,new GPUSetFactory());
	}
	@Test
	void testBitSet2() {
		test2(10000,new BitSetFactory());
	}
	@Test
	void testGPUSet2() {
		test2(10000,new GPUSetFactory());
	}
	void test1(int N,ISetFactory factory) {
		ArrayList<ISet> multiples=new ArrayList<>(N);
		for(int i=0;i<N;i++)
		{
			multiples.add(i,factory.createSet(N) );
		}
		System.out.println(multiples.size());
		for(int i=1;i<N;i++) {
			for(int j=0;j<N;j++) {
				if(j%i==0) multiples.get(i).add(j);
			}
		}
		ISet a=factory.createSet(N);
		ISet premiers=factory.createSet(N);
		premiers.add(1);
		System.out.println(a);
		for(int i=2;i<N;i++) {		
			ISet other=a.clone();
			a.addAll(multiples.get(i));
			if(!other.equals(a))
				premiers.add(i);
		}
		System.out.println(premiers.cardinality());
	}
	void test2(int N,ISetFactory factory) {
		ArrayList<ISet> multiples=new ArrayList<>(N);
		for(int i=0;i<N;i++)
		{
			multiples.add(i,factory.createSet(N) );
		}
		System.out.println(multiples.size());
		for(int i=1;i<N;i++) {
			for(int j=0;j<N;j++) {
				if(j%i==0) multiples.get(i).add(j);
			}
		}
		ISet premiers=factory.createSet(N);
		premiers.fill(N);
		for(int i=2;i<N;i++) {		
			ISet other=multiples.get(i).clone();
			other.remove(i);
			premiers.removeAll(other);
		}
		System.out.println(premiers.cardinality());
	}

}
