package fr.lirmm.fca4j.algo;

public interface AbstractAlgo<T> extends Runnable {

    public T getResult();

    public String getDescription();
}
