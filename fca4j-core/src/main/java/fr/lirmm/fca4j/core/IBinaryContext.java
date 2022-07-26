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
package fr.lirmm.fca4j.core;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

public interface IBinaryContext extends Cloneable {

    public ISetFactory getFactory();

    public int addAttribute(String name, ISet extent);

    public int addObject(String name, ISet intent);

    public int addAttributeName(String name);

    public String getAttributeName(int i);

    public void setAttributeName(int i, String newName);

    public String getObjectName(int i);

    public void setObjectName(int i, String newName);

    public int addObjectName(String name);

    public int getAttributeCount();

    public int getObjectCount();

    public boolean get(int numObject, int numAttribute);

    public void set(int numObject, int numAttribute, boolean value);

    public ISet getExtent(int numAttribute);

    public ISet getIntent(int numObject);

    public void setExtent(int numAttribute, ISet extent);

    public void setIntent(int numObject, ISet intent);

    public double getObjectsDensity();

    public double getAttributesDensity();

    public double getDensity();

    public double getDataComplexity();
    
	public double getSchuttNumber();
    
    public int getIncidence();

    public String getName();

    public void setName(String name);

    public String getDescription();

    public int getObjectIndex(String nameObject);

    public int getAttributeIndex(String nameObject);

    public IBinaryContext transpose();

    public IBinaryContext inverse();

    public void removeAttribute(int numAttr);

    public void removeObject(int numObj);

    public IBinaryContext clone();

    public IBinaryContext clone(ISetFactory factory);

}
