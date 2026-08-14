@echo off
REM ===================================================================
REM  bench_ceres_natif.bat
REM
REM  Compare le portage C de Ceres a la version Java, sur les cinq
REM  contextes du corpus. Deux passes par contexte : -native puis sans,
REM  dans la MEME JVM par passe mais des JVM distinctes entre les deux,
REM  pour qu'aucune des deux ne beneficie du JIT chauffe par l'autre.
REM
REM  Le nombre de concepts et d'aretes est affiche pour chaque passe :
REM  c'est le controle d'equivalence a l'echelle reelle. Le harnais
REM  ceres_test valide 56178 petits contextes ; ces cinq-la sont les
REM  seuls a exercer les grandes tailles.
REM
REM  Usage :
REM     bench_ceres_natif.bat            5 mesures
REM     bench_ceres_natif.bat /quick     1 mesure
REM     bench_ceres_natif.bat /build     mvn package avant
REM ===================================================================
setlocal enabledelayedexpansion

set PROJECT_ROOT=C:\platform\fca4j-project
set JAR=%PROJECT_ROOT%\fca4j-app-light\target\fca4j-app-light-0.4.7-jar-with-dependencies.jar
set OUTDIR=C:\ClaudeData

REM Profilage neutralise : CeresProfile et le rapport de latticecbo s'activent
REM tous deux sur FCA4J_PROFILE, et leur cout fausserait la mesure.
set FCA4J_PROFILE=
set FCA4J_TLOG=

set REPEATS=5
set WARMUP=5
set DO_BUILD=0
for %%A in (%*) do (
    if /I "%%A"=="/quick" set REPEATS=1
    if /I "%%A"=="/build" set DO_BUILD=1
)

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set LDT=%%I
set STAMP=!LDT:~0,8!_!LDT:~8,4!
set LOG=%OUTDIR%\ceres_c_vs_java_!STAMP!.log

set CTX1=C:\projects\rules\chess\chess.slf
set CTX2=C:\projects\rules\ord6magic04\ord6magic04.slf
set CTX3=C:\projects\rules\ord10shuttle\ord10shuttle.slf
set CTX4=C:\projects\monstre\ProtSystem.slf
set CTX5=C:\projects\monstre\Plant.slf

if "%DO_BUILD%"=="1" (
    echo   mvn package...
    cd /d "%PROJECT_ROOT%"
    call mvn -q -DskipTests package
    if errorlevel 1 goto :fail
)
if not exist "%JAR%" (
    echo   ECHEC : jar introuvable : %JAR%
    goto :fail
)

echo ============================================================
echo  Ceres : portage C contre Java
echo ============================================================
echo   chauffe %WARMUP%, mesures %REPEATS%
echo   sortie  %LOG%
echo.

> "%LOG%" echo === Ceres : C contre Java  %STAMP% ===
>>"%LOG%" echo chauffe=%WARMUP% mesures=%REPEATS%
>>"%LOG%" echo.

set IDX=0
for %%C in ("%CTX1%" "%CTX2%" "%CTX3%" "%CTX4%" "%CTX5%") do (
    set /a IDX+=1
    echo   [!IDX!/5] %%~nxC
    >>"%LOG%" echo ############################################################
    >>"%LOG%" echo # %%~nxC
    >>"%LOG%" echo ############################################################
    >>"%LOG%" echo --- natif C ---
    java -cp "%JAR%" fr.lirmm.fca4j.main.AocBench ^
         -w %WARMUP% -r %REPEATS% -a CERES -m BITSET_PACKED -native %%C >> "%LOG%" 2>&1
    if errorlevel 1 (echo   ECHEC natif sur %%~nxC & goto :fail)
    >>"%LOG%" echo --- java ---
    java -cp "%JAR%" fr.lirmm.fca4j.main.AocBench ^
         -w %WARMUP% -r %REPEATS% -a CERES -m BITSET_PACKED %%C >> "%LOG%" 2>&1
    if errorlevel 1 (echo   ECHEC java sur %%~nxC & goto :fail)
    >>"%LOG%" echo.
)

echo.
echo ============================================================
echo  Recapitulatif
echo ============================================================
REM Les lignes CERES portent le moteur, les temps, les concepts et les
REM aretes. Deux lignes consecutives = meme contexte, C puis Java : les
REM comptes de concepts et d'aretes DOIVENT coincider.
findstr /C:"CERES" "%LOG%"
echo.
echo   Detail : %LOG%
echo.
goto :end

:fail
echo.
echo  COMPARAISON INTERROMPUE
endlocal
exit /b 1

:end
endlocal
exit /b 0