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
