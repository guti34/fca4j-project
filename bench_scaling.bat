@echo off
REM ===================================================================
REM  bench_scaling.bat
REM
REM  Balayage en |G| : mesure les quatre algorithmes d'AOC-poset sur des
REM  contextes de taille croissante, pour distinguer un ecart ALGORITHMIQUE
REM  d'un cout d'IMPLEMENTATION.
REM
REM  La question : sur 717 objets Hermes met 3.0x le temps de Ceres, sur
REM  19020 objets il en met 12.3x, pour un resultat identique. Un facteur
REM  qui CROIT avec le nombre d'objets ne s'explique pas par l'algorithme.
REM
REM  Deux familles :
REM    dup  lignes de base repliquees. Concepts et aretes RIGOUREUSEMENT
REM         constants d'une taille a l'autre : seuls les extents grossissent.
REM         Toute pente y est du cout par objet. C'est la famille qui tranche.
REM    rnd  contextes aleatoires, |G| croissant. Le nombre de concepts croit
REM         aussi. Sert de controle.
REM
REM  Usage :
REM     bench_scaling.bat              genere puis mesure les deux familles
REM     bench_scaling.bat /nogen       reutilise les contextes deja generes
REM     bench_scaling.bat /native      mesure les portages C au lieu du Java
REM     bench_scaling.bat /quick       tailles reduites, 3 mesures
REM ===================================================================
setlocal enabledelayedexpansion

set PROJECT_ROOT=C:\platform\fca4j-project
set JAR=%PROJECT_ROOT%\fca4j-app-light\target\fca4j-app-light-0.5.0-jar-with-dependencies.jar
set OUTDIR=C:\ClaudeData
set CTXDIR=%OUTDIR%\scaling

REM Profilage neutralise : il fausserait les temps.
set FCA4J_PROFILE=
set FCA4J_TLOG=

set DO_GEN=1
set NATIVE=
set SIZES=1000,2000,4000,8000,16000
set REPEATS=5
set WARMUP=5
set ATTRS=52
set DENSITY=0.30
set BASEROWS=250

for %%A in (%*) do (
    if /I "%%A"=="/nogen"  set DO_GEN=0
    if /I "%%A"=="/native" set NATIVE=-native
    if /I "%%A"=="/quick"  (set SIZES=1000,4000,16000& set REPEATS=3)
)

if not exist "%JAR%" (
    echo   ECHEC : jar introuvable : %JAR%
    goto :fail
)

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set LDT=%%I
set STAMP=!LDT:~0,8!_!LDT:~8,4!
set LOG=%OUTDIR%\scaling_!STAMP!.log

echo ============================================================
echo  [1/3] Generation des contextes
echo ============================================================
if "%DO_GEN%"=="0" (
    echo   Ignore ^(/nogen^).
    goto :skipgen
)
if not exist "%CTXDIR%" mkdir "%CTXDIR%"
java -cp "%JAR%" fr.lirmm.fca4j.main.ScalingGen "%CTXDIR%" ^
     -attrs %ATTRS% -density %DENSITY% -sizes %SIZES% -base %BASEROWS%
if errorlevel 1 goto :fail
:skipgen

if not exist "%CTXDIR%\liste_dup.txt" (
    echo   ECHEC : listes absentes, relancer sans /nogen
    goto :fail
)
set /p LISTE_DUP=<"%CTXDIR%\liste_dup.txt"
set /p LISTE_RND=<"%CTXDIR%\liste_rnd.txt"

echo.
echo ============================================================
echo  [2/3] Mesure
echo ============================================================
echo   moteur  : %NATIVE% ^(vide = java^)
echo   chauffe %WARMUP%, mesures %REPEATS%
echo   sortie  %LOG%
echo.

> "%LOG%" echo === balayage en ^|G^|  %STAMP%  moteur=%NATIVE% ===
>>"%LOG%" echo attrs=%ATTRS% densite=%DENSITY% base=%BASEROWS% tailles=%SIZES%
>>"%LOG%" echo.

echo   famille dup ^(concepts constants^)...
>>"%LOG%" echo ############################################################
>>"%LOG%" echo # famille dup : lignes repliquees, concepts constants
>>"%LOG%" echo ############################################################
java -cp "%JAR%" fr.lirmm.fca4j.main.AocBench -w %WARMUP% -r %REPEATS% ^
     -m BITSET_PACKED -a ARES,HERMES,CERES,PLUTON %NATIVE% %LISTE_DUP% >> "%LOG%" 2>&1
if errorlevel 1 goto :fail

echo   famille rnd ^(controle^)...
>>"%LOG%" echo.
>>"%LOG%" echo ############################################################
>>"%LOG%" echo # famille rnd : aleatoire, concepts croissants
>>"%LOG%" echo ############################################################
java -cp "%JAR%" fr.lirmm.fca4j.main.AocBench -w %WARMUP% -r %REPEATS% ^
     -m BITSET_PACKED -a ARES,HERMES,CERES,PLUTON %NATIVE% %LISTE_RND% >> "%LOG%" 2>&1
if errorlevel 1 goto :fail

echo.
echo ============================================================
echo  [3/3] Resultats
echo ============================================================
findstr /C:"x " /C:"ARES" /C:"HERMES" /C:"CERES" /C:"PLUTON" "%LOG%"
echo.
echo   Detail : %LOG%
echo.
echo   A lire : dans la famille dup, les colonnes concepts et aretes
echo   doivent etre IDENTIQUES sur toutes les tailles. Si le temps d'un
echo   algorithme y croit plus vite que celui des autres, l'ecart est
echo   dans l'implementation, pas dans l'algorithme.
echo.
goto :end

:fail
echo.
echo  BALAYAGE INTERROMPU
endlocal
exit /b 1

:end
endlocal
exit /b 0