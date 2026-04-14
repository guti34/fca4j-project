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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.regex.Pattern;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;

public class DlgpUtils {

	    private static final Pattern L_IDENT =
	            Pattern.compile("^[a-z][a-zA-Z0-9_]*$");

	    private static final Pattern IRIREF =
	            Pattern.compile("^<[^<>\"{}|^`\\\\\\s]+>$");

	    private static final Pattern PREFIXED_NAME =
	            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*:[a-zA-Z][a-zA-Z0-9_-]*$");

	    /**
	     * Retourne un prédicat DLGP valide.
	     * - Si le nom est déjà valide : retourne tel quel.
	     * - Sinon : retourne un <IRI> construit à partir du nom de domaine.
	     *
	     * @param predicateName nom du prédicat (ex: "Parent Of")
	     * @param baseDomain    base de l'IRI (ex: "http://example.org")
	     * @return prédicat DLGP valide
	     */
	    public static String getValidPredicate(String predicateName, String baseDomain) {

	        if (predicateName == null || predicateName.isEmpty()) {
	            throw new IllegalArgumentException("Predicate name cannot be null or empty");
	        }
	        if (baseDomain == null || baseDomain.isEmpty()) {
	            throw new IllegalArgumentException("Base domain cannot be null or empty");
	        }

	        // si déjà conforme, on retourne tel quel
	        if (isValidPredicate(predicateName)) {
	            return predicateName;
	        }

	        // garantir que la base se termine par / ou #
	        if (!baseDomain.endsWith("/") && !baseDomain.endsWith("#")) {
	            baseDomain += "/";
	        }

	        // normaliser Unicode (supprime accents)
	        String normalized = Normalizer.normalize(predicateName, Normalizer.Form.NFD)
	                .replaceAll("\\p{M}", "");

	        // encoder les caractères spéciaux pour l’IRI
	        String encoded=normalized;
			try {
				encoded = URLEncoder.encode(normalized, "UTF_8")
				        .replaceAll("\\+", "%20");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // remplacer + par %20 pour les espaces

	        return "<" + baseDomain + encoded + ">";
	    }

	    private static boolean isValidPredicate(String predicate) {
	        return L_IDENT.matcher(predicate).matches()
	                || IRIREF.matcher(predicate).matches()
	                || PREFIXED_NAME.matcher(predicate).matches();
	    }
		public static String buildConjunction(ISet intent,String[] attrNames) {
			StringBuilder sb=new StringBuilder();
			boolean first = true;
			for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
				int attr = it.next();
				if (!first) {
					sb.append(",");
				}
				first = false;
				String predicateName = getValidPredicate(attrNames[attr], "http://www.lirmm.fr");
				sb.append(predicateName + "(X)");
			}
			return sb.toString();
		}

	    // Exemple rapide
	    public static void main(String[] args) {
	        System.out.println(getValidPredicate("parent", "http://example.org"));
	        // parent (déjà valide)

	        System.out.println(getValidPredicate("Parent Of", "http://example.org"));
	        // <http://example.org/Parent%20Of>

	        System.out.println(getValidPredicate("example(domain)#1", "http://example.org"));
	        // <http://example.org/example%28domain%29%231>

	        System.out.println(getValidPredicate("ex:pred", "http://example.org"));
	        // ex:pred (PrefixedName valide)
	    }
	}