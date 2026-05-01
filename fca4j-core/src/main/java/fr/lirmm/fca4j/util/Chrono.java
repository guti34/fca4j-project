/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.util;

/**
 * This class records series of times collected during a process 
 *
 * @author agutierr et marianne huchard pour Instant
 */
import java.time.Duration;
import java.time.Instant;

/**
 * This class records series of times collected during a process 
 *
 * @author agutierr et marianne huchard pour startInstant et stopInstant
 */


import java.util.ArrayList;
import java.util.HashMap;

// TODO: Auto-generated Javadoc
/**
 * The Class Chrono.
 */
public class Chrono {
	private String name;
	protected HashMap<String,Instant> current_chronos=new HashMap<>();
	protected HashMap<String,ArrayList<Duration>> results=new HashMap<>();
	
	/**
	 * Instantiates a new chrono.
	 *
	 * @param name the name
	 */
	public Chrono(String name)
	{
		this.name=name;
	}
	
	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Start.
	 *
	 * @param serieName the serie name
	 */
	public void start(String serieName)
	{
		current_chronos.put(serieName, Instant.now());
	}
	
	/**
	 * Stop.
	 *
	 * @param serieName the serie name
	 */
	public void stop(String serieName)
	{
		Instant currentTime=current_chronos.get(serieName);
		if(currentTime!=null) 
			{
			ArrayList<Duration>result=results.get(serieName);
			if(result==null)
			{
				result=new ArrayList<>();
				results.put(serieName, result);
			}
			result.add(Duration.between(currentTime,Instant.now()));
			}
	}
	
	/**
	 * Gets the result.
	 *
	 * @param serieName the serie name
	 * @return the result
	 */
	public long getResult(String serieName)
	{
		long l=0L;
		for(Duration mesure:results.get(serieName)) l+=mesure.toMillis();
		return l;
	}
	
	/**
	 * Gets the result array.
	 *
	 * @param serieName the serie name
	 * @return the result array
	 */
	public ArrayList<Duration> getResultArray(String serieName)
	{
		return results.get(serieName);
	}
	
	/**
	 * Gets the result.
	 *
	 * @return the result
	 */
	public long getResult()
	{
		long l=0L;
		for(ArrayList<Duration> mesures:results.values())
			for(Duration mesure:mesures) l+=mesure.toMillis();
		return l;
	}
	
	/**
	 * Gets the serie count.
	 *
	 * @return the serie count
	 */
	public int getSerieCount()
	{
		return results.size();
	}
	
	/**
	 * Gets the serie names.
	 *
	 * @return the serie names
	 */
	public String[] getSerieNames()
	{
		return results.keySet().toArray(new String[results.keySet().size()]);
	}
}
