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

import java.util.List;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * The Interface IBinaryContext.
 */
public interface IBinaryContext extends Cloneable {

    /**
     * Gets the factory.
     *
     * @return the factory
     */
    public ISetFactory getFactory();

    /**
     * Adds the attribute.
     *
     * @param name the name
     * @param extent the extent
     * @return the int
     */
    public int addAttribute(String name, ISet extent);

    /**
     * Adds the object.
     *
     * @param name the name
     * @param intent the intent
     * @return the int
     */
    public int addObject(String name, ISet intent);

    /**
     * Adds the attribute name.
     *
     * @param name the name
     * @return the int
     */
    public int addAttributeName(String name);

    /**
     * Gets the attribute name.
     *
     * @param i the i
     * @return the attribute name
     */
    public String getAttributeName(int i);

    /**
     * Sets the attribute name.
     *
     * @param i the i
     * @param newName the new name
     */
    public void setAttributeName(int i, String newName);

    /**
     * Gets the object name.
     *
     * @param i the i
     * @return the object name
     */
    public String getObjectName(int i);

    /**
     * Sets the object name.
     *
     * @param i the i
     * @param newName the new name
     */
    public void setObjectName(int i, String newName);

    /**
     * Adds the object name.
     *
     * @param name the name
     * @return the int
     */
    public int addObjectName(String name);

    /**
     * Gets the attribute count.
     *
     * @return the attribute count
     */
    public int getAttributeCount();

    /**
     * Gets the object count.
     *
     * @return the object count
     */
    public int getObjectCount();

    /**
     * Gets the.
     *
     * @param numObject the num object
     * @param numAttribute the num attribute
     * @return true, if successful
     */
    public boolean get(int numObject, int numAttribute);

    /**
     * Sets the.
     *
     * @param numObject the num object
     * @param numAttribute the num attribute
     * @param value the value
     */
    public void set(int numObject, int numAttribute, boolean value);

    /**
     * Gets the extent.
     *
     * @param numAttribute the num attribute
     * @return the extent
     */
    public ISet getExtent(int numAttribute);
    
    /**
     * Gets the extents.
     *
     * @return the extents
     */
    public List<ISet> getExtents();

    /**
     * Gets the intent.
     *
     * @param numObject the num object
     * @return the intent
     */
    public ISet getIntent(int numObject);
    
    /**
     * Gets the intents.
     *
     * @return the intents
     */
    public List<ISet> getIntents();

    /**
     * Sets the extent.
     *
     * @param numAttribute the num attribute
     * @param extent the extent
     */
    public void setExtent(int numAttribute, ISet extent);

    /**
     * Sets the intent.
     *
     * @param numObject the num object
     * @param intent the intent
     */
    public void setIntent(int numObject, ISet intent);

    /**
     * Gets the objects density.
     *
     * @return the objects density
     */
    public double getObjectsDensity();

    /**
     * Gets the attributes density.
     *
     * @return the attributes density
     */
    public double getAttributesDensity();

    /**
     * Gets the density.
     *
     * @return the density
     */
    public double getDensity();

    /**
     * Gets the data complexity.
     *
     * @return the data complexity
     */
    public double getDataComplexity();
    
	/**
	 * Gets the schutt number.
	 *
	 * @return the schutt number
	 */
	public double getSchuttNumber();
    
    /**
     * Gets the incidence.
     *
     * @return the incidence
     */
    public int getIncidence();

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName();

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(String name);

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription();

    /**
     * Gets the object index.
     *
     * @param nameObject the name object
     * @return the object index
     */
    public int getObjectIndex(String nameObject);

    /**
     * Gets the attribute index.
     *
     * @param nameObject the name object
     * @return the attribute index
     */
    public int getAttributeIndex(String nameObject);

    /**
     * Transpose.
     *
     * @return the i binary context
     */
    public IBinaryContext transpose();

    /**
     * Inverse.
     *
     * @return the i binary context
     */
    public IBinaryContext inverse();

    /**
     * Removes the attribute.
     *
     * @param numAttr the num attr
     */
    public void removeAttribute(int numAttr);

    /**
     * Removes the object.
     *
     * @param numObj the num obj
     */
    public void removeObject(int numObj);

    /**
     * Clone.
     *
     * @return the i binary context
     */
    public IBinaryContext clone();

    /**
     * Clone.
     *
     * @param factory the factory
     * @return the i binary context
     */
    public IBinaryContext clone(ISetFactory factory);

}
