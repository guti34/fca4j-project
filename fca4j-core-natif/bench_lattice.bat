@echo off
REM ===================================================================
REM  bench_lattice.bat
REM
REM  Compare des ALGORITHMES de construction de treillis sur un corpus
REM  de contextes.
REM
REM  Deux questions auxquelles ce banc sert a repondre :
REM    1. PARALLEL_CBO est-il devenu meilleur qu'ADD_EXTENT, et sur
REM       quelles formes de contexte ? PARALLEL_CBO paie une phase
REM       couvertures separee, ADD_EXTENT maintient l'ordre pendant la
REM       construction : l'ecart peut tenir a la conception, pas a
REM       l'implementation.
REM    2. Un contexte de forme inhabituelle expose-t-il un comportement
REM       catastrophique invisible sur nos deux exemples ?
REM
REM  Precautions de mesure : alternance des configurations, N passes,
REM  minimum retenu, pause entre les runs.
REM
REM  Usage :
REM     bench_lattice.bat                     corpus, 3 passes
REM     bench_lattice.bat /reps 5
REM     bench_lattice.bat /pause 10
REM     bench_lattice.bat /ctx C:\chemin\contexte.slf
REM     bench_lattice.bat /nojava             natif seulement, bien plus rapide
REM
REM  ETENDRE LE CORPUS : ajouter une ligne call :BENCH dans la section
REM  CORPUS ci-dessous. C'est tout.
REM
REM  Pieges cmd deja rencontres, a ne pas reintroduire : pas de signe
REM  pourcent dans un commentaire, pas de parenthese dans un libelle
REM  passe a un sous-programme, pas d'indirection du type
REM  call set X=percent-percent VAR percent-percent dans un corps de for.
REM ===================================================================
setlocal enabledelayedexpansion

set JAR=C:\platform\fca4j-project\fca4j-app-light\target\fca4j-app-light-0.4.7-jar-with-dependencies.jar
set LOGDIR=%TEMP%\fca4j-bench

set REPS=3
set PAUSE_S=5
set ONECTX=
set DOJAVA=1

:parse
if "%~1"=="" goto endparse
if /I "%~1"=="/reps"   ( set REPS=%~2& shift & shift & goto parse )
if /I "%~1"=="/pause"  ( set PAUSE_S=%~2& shift & shift & goto parse )
if /I "%~1"=="/ctx"    ( set ONECTX=%~2& shift & shift & goto parse )
if /I "%~1"=="/nojava" ( set DOJAVA=0& shift & goto parse )
echo Option inconnue : %~1
goto :eof
:endparse

if not exist "%JAR%" (
    echo ECHEC : jar introuvable : %JAR%
    echo   -^> lancer build-native.bat d'abord.
    goto :eof
)
if not exist "%LOGDIR%" mkdir "%LOGDIR%"

set FCA4J_PROFILE=1
set FCA4J_THREADS=
set FCA4J_SPAWN=
set FCA4J_SPAWNDEPTH=
set FCA4J_MAXTASKS=

echo.
echo ============================================================
echo  Banc treillis   reps=%REPS%  pause=%PAUSE_S%s  java=%DOJAVA%
echo  Journaux : %LOGDIR%
echo ============================================================

if not "%ONECTX%"=="" (
    for %%F in ("%ONECTX%") do call :BENCH "%%~nF" "%ONECTX%"
    goto done
)

REM ================= CORPUS =================
REM call :BENCH "inter3magic04" "C:\projects\monstre\inter3magic04.slf"
REM call :BENCH "ord10shuttle"  "C:\projects\rules\ord10shuttle\ord10shuttle.slf"
call :BENCH "ord5bikesharing_day_cut"  "C:\projects\monstre\ord5bikesharing_day_cut.slf"
call :BENCH "inter5shuttle" "C:\projects\monstre\inter5shuttle.slf"
call :BENCH "inter6shuttle" "C:\projects\rules\inter6shuttle\inter6shuttle.slf"

REM ==========================================

:done
echo.
echo ============================================================
echo  Termine. Profils complets dans %LOGDIR%
echo ============================================================
set FCA4J_PROFILE=
goto :eof


REM ===================================================================
REM  :BENCH  <nom-court> <chemin-contexte>
REM ===================================================================
:BENCH
set CTXNAME=%~1
set CTXFILE=%~2
if not exist "%CTXFILE%" (
    echo.
    echo   ATTENTION : contexte introuvable, ignore : %CTXFILE%
    goto :eof
)

echo.
echo ------------------------------------------------------------
echo  Contexte : %CTXNAME%
echo ------------------------------------------------------------

for /L %%C in (1,1,4) do set MIN_%%C=999999
set SZ_REF=
set MISMATCH=0

for /L %%R in (1,1,%REPS%) do (
    echo.
    echo   -- passe %%R / %REPS% --
    call :RUNCFG 1 "PARALLEL_CBO natif " PARALLEL_CBO 1 %%R
    call :RUNCFG 3 "ADD_EXTENT natif   " ADD_EXTENT   1 %%R
    if "%DOJAVA%"=="1" call :RUNCFG 2 "PARALLEL_CBO java  " PARALLEL_CBO 0 %%R
    if "%DOJAVA%"=="1" call :RUNCFG 4 "ADD_EXTENT java    " ADD_EXTENT   0 %%R
)

echo.
echo   RESULTAT %CTXNAME% : meilleure duree totale sur %REPS% passes
echo   ----------------------------------------------------------
call :SHOWMIN 1 "PARALLEL_CBO natif"
call :SHOWMIN 3 "ADD_EXTENT   natif"
if "%DOJAVA%"=="1" call :SHOWMIN 2 "PARALLEL_CBO java "
if "%DOJAVA%"=="1" call :SHOWMIN 4 "ADD_EXTENT   java "
echo   taille du JSON produit : %SZ_REF% octets
if "%MISMATCH%"=="1" echo   ALERTE : la taille du resultat varie selon l'algorithme,
if "%MISMATCH%"=="1" echo            les treillis peuvent differer. Verifier.
goto :eof


REM ===================================================================
REM  :RUNCFG  <index> <libelle> <algo> <natif 0/1> <passe>
REM ===================================================================
:RUNCFG
set IDX=%~1
set LABEL=%~2
set ALGO=%~3
set USENAT=%~4
set REPNO=%~5
set LOGF=%LOGDIR%\%CTXNAME%_cfg%IDX%_r%REPNO%.log
set RESJ=%LOGDIR%\%CTXNAME%_result.json

set NATFLAG=
if "%USENAT%"=="1" set NATFLAG=-native

java -Xmx8g -jar "%JAR%" LATTICE "%CTXFILE%" "%RESJ%" ^
     -a %ALGO% -i SLF -o JSON -m BITSET_PACKED %NATFLAG% > "%LOGF%" 2>&1

REM  "duration: 1002 ms"  -> jeton 2
set VAL=
for /f "tokens=2" %%A in ('findstr /C:"duration:" "%LOGF%"') do set VAL=%%A
if "!VAL!"=="" goto RUNFAIL

REM  Controle d'equivalence par la taille du resultat : deux algorithmes
REM  qui produisent le meme treillis produisent le meme JSON, a l'ordre
REM  des concepts pres. Une taille differente est un signal, pas une
REM  preuve, mais une taille identique est rassurante.
set SZ=
for %%F in ("%RESJ%") do set SZ=%%~zF
if not defined SZ_REF set SZ_REF=!SZ!
if not "!SZ!"=="!SZ_REF!" set MISMATCH=1

if !VAL! LSS !MIN_%IDX%! set MIN_%IDX%=!VAL!
echo      %LABEL%  duree=!VAL! ms   json=!SZ! o
goto RUNPAUSE

:RUNFAIL
echo      %LABEL%  ECHEC ou non supporte, voir %LOGF%
goto RUNPAUSE

:RUNPAUSE
if %PAUSE_S% GTR 0 timeout /t %PAUSE_S% /nobreak >nul 2>&1
goto :eof


REM ===================================================================
REM  :SHOWMIN  <index> <libelle SANS parenthese>
REM ===================================================================
:SHOWMIN
set IDX=%~1
set V=!MIN_%IDX%!
if "!V!"=="999999" goto SHOWNONE
set PCT=
if not "!MIN_1!"=="999999" set /a PCT=^(!MIN_1! - !V!^) * 1000 / !MIN_1!
if defined PCT echo     %~2 : !V! ms   [!PCT! pour mille vs PARALLEL_CBO natif]
if not defined PCT echo     %~2 : !V! ms
goto :eof
:SHOWNONE
echo     %~2 : aucune mesure
goto :eof