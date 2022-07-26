/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package fr.lirmm.fca4j.main;

import java.util.HashSet;
import java.util.TreeSet;

import fr.lirmm.fca4j.iset.AbstractSetContext;
import fr.lirmm.fca4j.iset.roaringbitmap.RoaringBitMapFactory;
import fr.lirmm.fca4j.iset.std.ArrayListSetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.iset.std.BoolArrayFactory;
import fr.lirmm.fca4j.iset.std.IntArrayFactory;
import fr.lirmm.fca4j.iset.std.JavaCollectionSetFactory;
import fr.lirmm.fca4j.iset.std.SparseBitSetFactory;

public class SetContextLight extends AbstractSetContext{

	public SetContextLight() {
		registerFactory(new BitSetFactory());
//		registerFactory(new GPUSetFactory());
		registerFactory(new RoaringBitMapFactory());
		registerFactory(new SparseBitSetFactory());
		registerFactory(new JavaCollectionSetFactory<>(() -> new HashSet<>(),false));
		registerFactory( new JavaCollectionSetFactory<>(() -> new TreeSet<>(),true));
		registerFactory( new IntArrayFactory());
		registerFactory( new ArrayListSetFactory());
		registerFactory( new BoolArrayFactory());
	}
	
	@Override
	public String getDefaultImplementation() {
		return "BITSET";
	}

}
