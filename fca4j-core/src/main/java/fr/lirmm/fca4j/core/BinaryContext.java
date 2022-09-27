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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * The Class BinaryContext.
 */
public class BinaryContext implements IBinaryContext, Cloneable {

	protected String name;
	protected String description;
	protected ArrayList<String> objectNames = new ArrayList<>();
	protected ArrayList<String> attrNames = new ArrayList<>();
	protected ArrayList<ISet> rows = new ArrayList<>();
	protected ArrayList<ISet> columns = new ArrayList<>();
	protected ISetFactory factory;

	/**
	 * Instantiates a new binary context.
	 *
	 * @param nbObjects the nb objects
	 * @param nbAttributes the nb attributes
	 * @param name the name
	 * @param factory the factory
	 */
	public BinaryContext(int nbObjects, int nbAttributes, String name, ISetFactory factory) {
		this.name = name;
		this.factory = factory;
		// reserve space to store relations
		rows = new ArrayList<>(nbObjects);
		columns = new ArrayList<>(nbAttributes);
		for (int i = 0; i < nbAttributes; i++) {
			columns.add(factory.createSet(nbObjects));
		}
		for (int i = 0; i < nbObjects; i++) {
			rows.add(factory.createSet(nbAttributes));
		}
	}

	/**
	 * Instantiates a new binary context.
	 *
	 * @param rows the rows
	 * @param columns the columns
	 * @param name the name
	 * @param factory the factory
	 */
	public BinaryContext(ArrayList<ISet> rows, ArrayList<ISet> columns, String name, ISetFactory factory) {
		this.rows = rows;
		this.columns = columns;
		this.name = name;
		this.factory = factory;
	}

	/**
	 * Adds the attribute.
	 *
	 * @param name the name
	 * @param extent the extent
	 * @return the int
	 */
	@Override
	public int addAttribute(String name, ISet extent) {
		int num_attr = addAttributeName(name);
		this.columns.add(factory.clone(extent));
		Iterator<Integer> it = extent.iterator();
		while (it.hasNext()) {
			int numobj = it.next();
			rows.get(numobj).add(num_attr);
		}
		return num_attr;
	}

	/**
	 * Adds the attribute name.
	 *
	 * @param name the name
	 * @return the int
	 */
	@Override
	public int addAttributeName(String name) {
		attrNames.add(name);
		return attrNames.size() - 1;
	}

	/**
	 * Gets the attribute name.
	 *
	 * @param i the i
	 * @return the attribute name
	 */
	@Override
	public String getAttributeName(int i) {
		try {
			return attrNames.get(i);
		} catch (Exception e) {
			return "Attribute " + i;
		}
	}

	/**
	 * Gets the object name.
	 *
	 * @param i the i
	 * @return the object name
	 */
	@Override
	public String getObjectName(int i) {
		try {
			return objectNames.get(i);
		} catch (Exception e) {
			return "Object " + i;
		}
	}

	/**
	 * Adds the object.
	 *
	 * @param name the name
	 * @param intent the intent
	 * @return the int
	 */
	@Override
	public int addObject(String name, ISet intent) {
		int num_obj = addObjectName(name);
		this.rows.add(intent);
		Iterator<Integer> it = intent.iterator();
		while (it.hasNext()) {
			columns.get(it.next()).add(num_obj);
		}
		return num_obj;
	}

	/**
	 * Adds the object name.
	 *
	 * @param name the name
	 * @return the int
	 */
	@Override
	public int addObjectName(String name) {
		objectNames.add(name);
		return objectNames.size() - 1;
	}

	/**
	 * Gets the attribute count.
	 *
	 * @return the attribute count
	 */
	@Override
	public int getAttributeCount() {
		return columns.size();
	}

	/**
	 * Gets the object count.
	 *
	 * @return the object count
	 */
	@Override
	public int getObjectCount() {
		return rows.size();
	}

	/**
	 * Gets the.
	 *
	 * @param numObject the num object
	 * @param numAttribute the num attribute
	 * @return true, if successful
	 */
	@Override
	public boolean get(int numObject, int numAttribute) {
		return rows.get(numObject).contains(numAttribute);
	}

	/**
	 * Sets the.
	 *
	 * @param numObject the num object
	 * @param numAttribute the num attribute
	 * @param value the value
	 */
	@Override
	public void set(int numObject, int numAttribute, boolean value) {
		if (value) {
			rows.get(numObject).add(numAttribute);
			columns.get(numAttribute).add(numObject);
		} else {
			rows.get(numObject).remove(numAttribute);
			columns.get(numAttribute).remove(numObject);
		}
	}

	/**
	 * Gets the extent.
	 *
	 * @param numAttribute the num attribute
	 * @return the extent
	 */
	@Override
	public ISet getExtent(int numAttribute) {
		return columns.get(numAttribute);
	}

	/**
	 * Sets the extent.
	 *
	 * @param numAttribute the num attribute
	 * @param extent the extent
	 */
	@Override
	public void setExtent(int numAttribute, ISet extent) {
		columns.set(numAttribute, factory.clone(extent));
	}

	/**
	 * Gets the intent.
	 *
	 * @param numObject the num object
	 * @return the intent
	 */
	@Override
	public ISet getIntent(int numObject) {
		return rows.get(numObject);
	}

	/**
	 * Sets the intent.
	 *
	 * @param numObject the num object
	 * @param intent the intent
	 */
	@Override
	public void setIntent(int numObject, ISet intent) {
		rows.set(numObject, factory.clone(intent));
	}

	/**
	 * Gets the objects density.
	 *
	 * @return the objects density
	 */
	@Override
	public double getObjectsDensity() {
		return (double) getIncidence() / getAttributeCount();
	}

	/**
	 * Gets the attributes density.
	 *
	 * @return the attributes density
	 */
	@Override
	public double getAttributesDensity() {
		return (double) getIncidence() / getObjectCount();
	}

	/**
	 * Gets the incidence.
	 *
	 * @return the incidence
	 */
	@Override
	public int getIncidence() {
		int sum = 0;
		for (int i = 0; i < getObjectCount(); i++) {
			sum += getIntent(i).cardinality();
		}
		return sum;
	}

	/**
	 * Gets the density.
	 *
	 * @return the density
	 */
	@Override
	public double getDensity() {
		double val = 0d;
		val = (double) getIncidence() / (getObjectCount() * getAttributeCount());
		return val;

	}

	/**
	 * Gets the data complexity.
	 *
	 * @return the data complexity
	 */
	@Override
	public double getDataComplexity() {
		double val1 = getObjectsDensity();
		double val2 = getAttributesDensity();
		return Math.sqrt(val1 * val1 + val2 * val2);
	}
	
	/**
	 * Gets the schutt number.
	 *
	 * @return the schutt number
	 */
	@Override
	public double getSchuttNumber() {
		double exp=Math.sqrt(getIncidence()+1);
		double res=3*Math.pow(2.0, exp)/2-1;
		return res;
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 *
	 * @param name the new name
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Transpose.
	 *
	 * @return the i binary context
	 */
	@Override
	public IBinaryContext transpose() {
		BinaryContext transpose = new BinaryContext((ArrayList<ISet>) columns.clone(), (ArrayList<ISet>) rows.clone(),
				"transpose of " + name, factory);
		transpose.attrNames = (ArrayList<String>) objectNames.clone();
		transpose.objectNames = (ArrayList<String>) attrNames.clone();
		return transpose;
	}

	/**
	 * Inverse.
	 *
	 * @return the i binary context
	 */
	@Override
	public IBinaryContext inverse() {
		BinaryContext inverse = (BinaryContext) this.clone();
		for (int numattr = 0; numattr < getAttributeCount(); numattr++) {
			ISet extent = inverse.columns.get(numattr);
			ISet inv_extent = factory.createSet();
			inv_extent.fill(getObjectCount());
			inv_extent = inv_extent.newDifference(extent);
			inverse.columns.set(numattr, inv_extent);
		}
		for (int numobj = 0; numobj < getObjectCount(); numobj++) {
			ISet intent = inverse.rows.get(numobj);
			ISet inv_intent = factory.createSet();
			inv_intent.fill(getAttributeCount());
			inv_intent = inv_intent.newDifference(intent);
			inverse.rows.set(numobj, inv_intent);
		}
		return inverse;

	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	@Override
	public String getDescription() {
		return description == null ? "" : description;
	}

	/**
	 * Sets the description.
	 *
	 * @param description the new description
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Gets the object index.
	 *
	 * @param nameObject the name object
	 * @return the object index
	 */
	@Override
	public int getObjectIndex(String nameObject) {
		return objectNames.indexOf(nameObject);
	}

	/**
	 * Clone.
	 *
	 * @param newFactory the new factory
	 * @return the i binary context
	 */
	@Override
	public IBinaryContext clone(ISetFactory newFactory) {
		ArrayList<ISet> cloned_rows = new ArrayList<>();
		for (ISet row : rows) {
			assert row.toBitSet().length() <= getAttributeCount();
			ISet clonedRow = newFactory.createSet(row.toBitSet(), getAttributeCount());
			cloned_rows.add(clonedRow);
		}
		ArrayList<ISet> cloned_columns = new ArrayList<>();
		for (ISet col : columns) {
			ISet clonedCol = newFactory.createSet(col.toBitSet(), getObjectCount());
			cloned_columns.add(clonedCol);
		}
		BinaryContext clonedContext = new BinaryContext(cloned_rows, cloned_columns, name, newFactory);
		clonedContext.objectNames = (ArrayList<String>) objectNames.clone();
		clonedContext.attrNames = (ArrayList<String>) attrNames.clone();
		return clonedContext;
	}

	/**
	 * Clone.
	 *
	 * @return the i binary context
	 */
	@Override
	public IBinaryContext clone() {
		return clone(factory);
	}

	/**
	 * Gets the attribute index.
	 *
	 * @param nameAttr the name attr
	 * @return the attribute index
	 */
	@Override
	public int getAttributeIndex(String nameAttr) {
		return attrNames.indexOf(nameAttr);
	}

	/**
	 * Sets the attribute name.
	 *
	 * @param i the i
	 * @param newName the new name
	 */
	@Override
	public void setAttributeName(int i, String newName) {
		attrNames.set(i, newName);
	}

	/**
	 * Sets the object name.
	 *
	 * @param i the i
	 * @param newName the new name
	 */
	@Override
	public void setObjectName(int i, String newName) {
		objectNames.set(i, newName);
	}

	/**
	 * Removes the attribute.
	 *
	 * @param numAttr the num attr
	 */
	@Override
	public void removeAttribute(int numAttr) {
		ArrayList<ISet> newRows = new ArrayList<>();
		attrNames.remove(numAttr);
		columns.remove(numAttr);
		for (ISet row : rows) {
			ISet newRow = factory.createSet();
			for (Iterator<Integer> it = row.iterator(); it.hasNext();) {
				int attr = it.next();
				if (attr < numAttr) {
					newRow.add(attr);
				} else if (attr > numAttr) {
					newRow.add(attr - 1);
				}
			}
			newRows.add(newRow);
		}
		rows = newRows;
	}

	/**
	 * Removes the object.
	 *
	 * @param numObj the num obj
	 */
	@Override
	public void removeObject(int numObj) {
		ArrayList<ISet> newCols = new ArrayList<>();
		objectNames.remove(numObj);
		rows.remove(numObj);
		for (ISet col : columns) {
			ISet newCol = factory.createSet();
			for (Iterator<Integer> it = col.iterator(); it.hasNext();) {
				int obj = it.next();
				if (obj < numObj) {
					newCol.add(obj);
				} else if (obj > numObj) {
					newCol.add(obj - 1);
				}
			}
			newCols.add(newCol);
		}
		columns = newCols;
	}

	/**
	 * Gets the factory.
	 *
	 * @return the factory
	 */
	@Override
	public ISetFactory getFactory() {
		return factory;
	}

	/**
	 * Gets the extents.
	 *
	 * @return the extents
	 */
	@Override
	public List<ISet> getExtents() {
		return columns;
	}

	/**
	 * Gets the intents.
	 *
	 * @return the intents
	 */
	@Override
	public List<ISet> getIntents() {
		return rows;
	}

}
