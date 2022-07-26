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

public class Chrono {
	private String name;
	protected HashMap<String,Instant> current_chronos=new HashMap<>();
	protected HashMap<String,ArrayList<Duration>> results=new HashMap<>();
	public Chrono(String name)
	{
		this.name=name;
	}
	public String getName()
	{
		return name;
	}

	public void start(String serieName)
	{
		current_chronos.put(serieName, Instant.now());
	}
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
	public long getResult(String serieName)
	{
		long l=0L;
		for(Duration mesure:results.get(serieName)) l+=mesure.toNanos();
		return l/1_000_000L;
	}
	public ArrayList<Duration> getResultArray(String serieName)
	{
		return results.get(serieName);
	}
	public long getResult()
	{
		long l=0L;
		for(ArrayList<Duration> mesures:results.values())
			for(Duration mesure:mesures) l+=mesure.toNanos();
		return l/1_000_000L;
	}
	public int getSerieCount()
	{
		return results.size();
	}
	public String[] getSerieNames()
	{
		return results.keySet().toArray(new String[results.keySet().size()]);
	}
}
