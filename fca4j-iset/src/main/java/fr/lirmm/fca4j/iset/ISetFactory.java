package fr.lirmm.fca4j.iset;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.util.BitSet;

/**
 *
 * @author agutierr
 */
public interface ISetFactory {
	public boolean ordered();
	public boolean fixedSize();
	public String name();
    public ISet createSet();
    public ISet createSet(BitSet bitset,int size);
    public ISet createSet(BitSet bitset);
    public ISet createSet(int size);
    public ISet clone(ISet to_clone);
}
