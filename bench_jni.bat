@echo off
REM ===================================================================
REM  bench_jni.bat
REM
REM  Mesure la repartition du temps sur le chemin natif : preparation
REM  du contexte contre algorithme proprement dit.
REM
REM  Pourquoi : la comparaison C/Java de Ceres donne un classement qui
REM  suit la TAILLE DE LA MATRICE (|G| x |A|) et non la difficulte du
REM  probleme. chess, 0.24 M de cellules, le C gagne 1.66x ; ord10shuttle,
REM  3.83 M de cellules et seulement 239 concepts, le C perd. Le suspect
REM  est donc la preparation des donnees, pas l'algorithme.
REM
REM  Chaque appel natif emet sur stderr une ligne :
REM     [jni] ceres  43500 x 88  cellules 3.83 M | ctx X ms  algo Y ms ...
REM
REM  Usage :
REM     bench_jni.bat            les cinq contextes, Ceres
REM     bench_jni.bat /ares      Ares en plus (meme chemin, meme dette)
REM ===================================================================
setlocal enabledelayedexpansion

set PROJECT_ROOT=C:\platform\fca4j-project
set JAR=%PROJECT_ROOT%\fca4j-app-light\target\fca4j-app-light-0.5.0-jar-with-dependencies.jar
set OUTDIR=C:\ClaudeData

REM Active l'instrumentation JNI. CeresProfile s'active sur la meme variable,
REM mais on ne passe pas -Dfca4j.profile et le chemin natif n'y passe pas :
REM en mode -native, c'est NativeAOCPosetCeres qui tourne, pas AOC_poset_Ceres.
set FCA4J_PROFILE=1

set ALGOS=CERES
for %%A in (%*) do (
    if /I "%%A"=="/ares" set ALGOS=CERES,ARES
)

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set LDT=%%I
set STAMP=!LDT:~0,8!_!LDT:~8,4!
set LOG=%OUTDIR%\jni_frontiere_!STAMP!.log

set CTX1=C:\projects\rules\chess\chess.slf
set CTX2=C:\projects\rules\ord6magic04\ord6magic04.slf
set CTX3=C:\projects\rules\ord10shuttle\ord10shuttle.slf
set CTX4=C:\projects\monstre\ProtSystem.slf
set CTX5=C:\projects\monstre\Plant.slf

if not exist "%JAR%" (
    echo   ECHEC : jar introuvable : %JAR%
    goto :fail
)

echo ============================================================
echo  Frontiere JNI : preparation du contexte contre algorithme
echo ============================================================
echo   algos  %ALGOS%
echo   sortie %LOG%
echo.

> "%LOG%" echo === frontiere JNI  %STAMP% ===
>>"%LOG%" echo.

set IDX=0
for %%C in ("%CTX1%" "%CTX2%" "%CTX3%" "%CTX4%" "%CTX5%") do (
    set /a IDX+=1
    echo   [!IDX!/5] %%~nxC
    >>"%LOG%" echo ############################################################
    >>"%LOG%" echo # %%~nxC
    >>"%LOG%" echo ############################################################
    REM Une seule mesure apres 2 chauffes : on cherche une repartition, pas
    REM une performance. Chaque execution emet sa propre ligne [jni], donc
    REM les chauffes fournissent gratuitement une idee de la variance.
    java -cp "%JAR%" fr.lirmm.fca4j.main.AocBench ^
         -w 2 -r 1 -a %ALGOS% -m BITSET_PACKED -native %%C >> "%LOG%" 2>&1
    if errorlevel 1 (echo   ECHEC sur %%~nxC & goto :fail)
    >>"%LOG%" echo.
)

echo.
echo ============================================================
echo  Lignes de frontiere
echo ============================================================
findstr /C:"[jni]" "%LOG%"
echo.
echo   Detail : %LOG%
echo.
goto :end

:fail
echo.
echo  MESURE INTERROMPUE
endlocal
exit /b 1

:end
endlocal
exit /b 0