/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.core;

import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
    public class Implication {

        ISet premise;
        ISet conclusion;
        ISet support;

        public Implication(ISet premise, ISet conclusion,ISet support) {
            this.premise = premise;
            this.conclusion = conclusion.newDifference(premise);
            this.support=support;
        }
        public ISet getSupport(){
            return support;
        }
        public void setSupport(ISet support){
        	this.support=support;
        }
        public ISet getPremise(){
            return premise;
        }
        public ISet getConclusion(){
            return conclusion;
        }
        @Override
        public String toString() {
            return String.format("<%d> %s => %s",support.cardinality(),premise,conclusion );
        }
    }
