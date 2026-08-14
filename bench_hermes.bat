@echo off
REM ===================================================================
REM  bench_hermes.bat
REM
REM  Profile Hermes sur une progression de tailles, pour situer d'ou
REM  vient son exposant 2.31 en nombre de concepts (contre 1.88 pour
REM  Ceres) et son debit d'allocation de ~30 Go par execution.
REM
REM  Le profil est imprime par execution : avec -w 1 -r 1, deux rapports
REM  par contexte, le second decrivant une JVM chauffee.
REM
REM  On profile sur la famille rnd (concepts croissants) plutot que dup
REM  (concepts constants) : c'est le nombre de concepts qui fait exploser
REM  Hermes, le balayage l'a etabli.
REM
REM  Usage :
REM     bench_hermes.bat            rnd 1000..8000 + corpus reel
REM     bench_hermes.bat /big       ajoute rnd_16000 (27 s par execution)
REM     bench_hermes.bat /ceres     profile aussi Ceres, pour comparer
REM     bench_hermes.bat /pluton    profile Pluton a la place
REM     bench_hermes.bat /tous      Hermes, Pluton et Ceres
REM ===================================================================
setlocal enabledelayedexpansion

set PROJECT_ROOT=C:\platform\fca4j-project
set JAR=%PROJECT_ROOT%\fca4j-app-light\target\fca4j-app-light-0.4.7-jar-with-dependencies.jar
set OUTDIR=C:\ClaudeData
set CTXDIR=%OUTDIR%\scaling

REM Le profil s'active par propriete systeme, pas par variable
REM d'environnement : on neutralise cette derniere pour que le rapport de
REM latticecbo ne vienne pas polluer la sortie.
set FCA4J_PROFILE=
set FCA4J_TLOG=

set ALGOS=HERMES
set BIG=0
for %%A in (%*) do (
    if /I "%%A"=="/big"   set BIG=1
    if /I "%%A"=="/ceres"  set ALGOS=HERMES,CERES
    if /I "%%A"=="/pluton" set ALGOS=PLUTON
    if /I "%%A"=="/tous"   set ALGOS=HERMES,PLUTON,CERES
)

if not exist "%JAR%" (
    echo   ECHEC : jar introuvable : %JAR%
    goto :fail
)
if not exist "%CTXDIR%\rnd_1000x52.slf" (
    echo   ECHEC : contextes de balayage absents.
    echo   -^> lancer d'abord bench_scaling.bat
    goto :fail
)

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set LDT=%%I
set STAMP=!LDT:~0,8!_!LDT:~8,4!
set LOG=%OUTDIR%\profil_hermes_!STAMP!.log

echo ============================================================
echo  Profil Hermes
echo ============================================================
echo   algos  %ALGOS%
echo   sortie %LOG%
echo.

> "%LOG%" echo === profil Hermes  %STAMP% ===
>>"%LOG%" echo.

set LISTE="%CTXDIR%\rnd_1000x52.slf" "%CTXDIR%\rnd_2000x52.slf" "%CTXDIR%\rnd_4000x52.slf" "%CTXDIR%\rnd_8000x52.slf"
if "%BIG%"=="1" set LISTE=!LISTE! "%CTXDIR%\rnd_16000x52.slf"

REM Une JVM par contexte : begin() remet les compteurs a zero a chaque
REM execution, donc plusieurs contextes dans la meme JVM ne laisseraient
REM que le profil du dernier.
for %%C in (!LISTE!) do (
    echo   %%~nxC
    >>"%LOG%" echo ############################################################
    >>"%LOG%" echo # %%~nxC
    >>"%LOG%" echo ############################################################
    java -Dfca4j.profile=true -cp "%JAR%" fr.lirmm.fca4j.main.AocBench ^
         -w 1 -r 1 -m BITSET_PACKED -a %ALGOS% %%C >> "%LOG%" 2>&1
    if errorlevel 1 (echo   ECHEC sur %%~nxC & goto :fail)
    >>"%LOG%" echo.
)

REM Deux contextes reels, de formes opposees : ord6magic04 a beaucoup
REM d'objets pour peu d'attributs, ProtSystem l'inverse. Le second est
REM le cas ou minSetSize vaut |A| et non |G|, ce qui doit changer la
REM lecture des compteurs d'allocation.
for %%C in ("C:\projects\rules\ord6magic04\ord6magic04.slf" "C:\projects\monstre\ProtSystem.slf") do (
    if exist %%C (
        echo   %%~nxC
        >>"%LOG%" echo ############################################################
        >>"%LOG%" echo # %%~nxC
        >>"%LOG%" echo ############################################################
        java -Dfca4j.profile=true -cp "%JAR%" fr.lirmm.fca4j.main.AocBench ^
             -w 1 -r 1 -m BITSET_PACKED -a %ALGOS% %%C >> "%LOG%" 2>&1
        >>"%LOG%" echo.
    )
)

echo.
echo ============================================================
echo  Extrait
echo ============================================================
findstr /C:"HERMES" /C:"PLUTON" /C:"CERES" /C:"diagramme de Hasse" /C:"partition des" /C:"phase d'ordre" /C:"paires examinees" /C:"memoire allouee" "%LOG%"
echo.
echo   Profil complet : %LOG%
echo.
goto :end

:fail
echo.
echo  PROFILAGE INTERROMPU
endlocal
exit /b 1

:end
endlocal
exit /b 0