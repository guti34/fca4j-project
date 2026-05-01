/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


/**
 *
 * @author agutierr
 */
public interface IOrderedSet extends ISet{
    /**
     * Last element
     * 
     * @return last integer in set or {@code -1} if there is no such
     */
    public int last();
    
}
