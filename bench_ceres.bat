@echo off
REM ===================================================================
REM  bench_ceres.bat
REM
REM  Campagne de mesure de Ceres : audit d'equivalence, puis profil sur
REM  les cinq contextes du corpus, dans un fichier horodate.
REM
REM  L'audit passe EN PREMIER et le script s'arrete s'il echoue. Une
REM  optimisation qui change la sortie n'est pas une optimisation, et
REM  mesurer un resultat faux est du temps perdu.
REM
REM  Usage :
REM     bench_ceres.bat                 audit + profil, 5 mesures
REM     bench_ceres.bat /noaudit        profil seul (audit deja passe)
REM     bench_ceres.bat /quick          1 mesure au lieu de 5
REM     bench_ceres.bat /label v5       suffixe le fichier de sortie
REM
REM  Prerequis : mvn dans le PATH, jar deja construit ou /build.
REM     bench_ceres.bat /build          relance mvn package avant
REM ===================================================================
setlocal enabledelayedexpansion

set PROJECT_ROOT=C:\platform\fca4j-project
set JAR=%PROJECT_ROOT%\fca4j-app-light\target\fca4j-app-light-0.4.7-jar-with-dependencies.jar
set OUTDIR=C:\ClaudeData

REM --- Options --------------------------------------------------------
REM CeresProfile s'active aussi via la variable d'environnement FCA4J_PROFILE.
REM On la neutralise ici (setlocal la rend locale au script) pour que l'audit
REM ne deverse pas un profil de contexte 5x6 dans la sortie. Les mesures, elles,
REM passent -Dfca4j.profile=true en ligne de commande et restent instrumentees.
set FCA4J_PROFILE=

set DO_AUDIT=1
set DO_BUILD=0
set REPEATS=5
set WARMUP=5
set LABEL=

:parseargs
if "%~1"=="" goto endparse
if /I "%~1"=="/noaudit" set DO_AUDIT=0
if /I "%~1"=="/build"   set DO_BUILD=1
if /I "%~1"=="/quick"   set REPEATS=1
if /I "%~1"=="/label"   (set LABEL=_%~2& shift)
shift
goto parseargs
:endparse

REM Horodatage independant des parametres regionaux : on passe par WMIC
REM plutot que par %DATE%, dont le format varie d'une machine a l'autre.
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set LDT=%%I
set STAMP=!LDT:~0,8!_!LDT:~8,4!
set LOG=%OUTDIR%\profil_ceres_!STAMP!!LABEL!.log

REM --- Les cinq contextes du corpus -----------------------------------
REM  Ordre : les deux gros d'abord, ce sont eux qui decident.
set CTX1=C:\projects\rules\chess\chess.slf
set CTX2=C:\projects\rules\ord6magic04\ord6magic04.slf
set CTX3=C:\projects\rules\ord10shuttle\ord10shuttle.slf
set CTX4=C:\projects\monstre\ProtSystem.slf
set CTX5=C:\projects\monstre\Plant.slf

echo ============================================================
echo  [0/3] Verifications
echo ============================================================
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
if "%DO_BUILD%"=="1" (
    echo   mvn package...
    cd /d "%PROJECT_ROOT%"
    call mvn -q -DskipTests package
    if errorlevel 1 goto :fail
)
if not exist "%JAR%" (
    echo   ECHEC : jar introuvable : %JAR%
    echo   -^> relancer avec /build
    goto :fail
)
for %%F in ("%JAR%") do echo   jar  : %%~tF
for %%C in ("%CTX1%" "%CTX2%" "%CTX3%" "%CTX4%" "%CTX5%") do (
    if not exist %%C (
        echo   ECHEC : contexte introuvable : %%C
        goto :fail
    )
)
echo   contextes : 5 presents
echo   sortie    : %LOG%

echo.
echo ============================================================
echo  [1/3] Audit d'equivalence
echo ============================================================
if "%DO_AUDIT%"=="0" (
    echo   Ignore ^(/noaudit^).
    goto :skipaudit
)
REM AocAudit compare chaque algorithme a une reference sur un balayage
REM exhaustif puis sur des contextes aleatoires. Le changement de cette
REM passe touche fca4j-iset, donc TOUS les algorithmes : on les audite
REM tous, pas seulement Ceres.
java -cp "%JAR%" fr.lirmm.fca4j.algo.AocAudit > "%OUTDIR%\audit_ceres_!STAMP!.log" 2>&1
if errorlevel 1 (
    echo   ECHEC : AocAudit s'est termine en erreur.
    type "%OUTDIR%\audit_ceres_!STAMP!.log"
    goto :fail
)
REM AocAudit ne rend pas de code de retour : il imprime une ligne
REM "=> no discrepancy for X" par algorithme audite, et rien de tel si une
REM divergence est trouvee. On compte donc ces lignes : il en faut TROIS
REM (Ares, Pluton, Ceres). Chercher l'absence d'un marqueur d'echec serait
REM fragile ; compter les succes attendus ne l'est pas.
set OKCOUNT=0
for /f %%N in ('findstr /C:"no discrepancy" "%OUTDIR%\audit_ceres_!STAMP!.log" ^| find /C "no discrepancy"') do set OKCOUNT=%%N
type "%OUTDIR%\audit_ceres_!STAMP!.log"
if not "!OKCOUNT!"=="3" (
    echo.
    echo   ECHEC : !OKCOUNT! algorithmes sur 3 sans divergence.
    goto :fail
)
echo   Audit vert : 3/3.
:skipaudit

echo.
echo ============================================================
echo  [2/3] Profil sur les cinq contextes
echo ============================================================
echo   chauffe %WARMUP%, mesures %REPEATS%
echo.

> "%LOG%" echo === campagne Ceres  %STAMP%%LABEL% ===
>>"%LOG%" echo chauffe=%WARMUP% mesures=%REPEATS%
>>"%LOG%" echo.

set IDX=0
for %%C in ("%CTX1%" "%CTX2%" "%CTX3%" "%CTX4%" "%CTX5%") do (
    set /a IDX+=1
    echo   [!IDX!/5] %%~nxC
    >>"%LOG%" echo ############################################################
    >>"%LOG%" echo # %%~nxC
    >>"%LOG%" echo ############################################################
    REM Une JVM par contexte : CeresProfile.begin remet les compteurs a
    REM zero a chaque run(), donc plusieurs contextes dans la meme JVM ne
    REM laisseraient que le profil du dernier.
    java -Dfca4j.profile=true -cp "%JAR%" fr.lirmm.fca4j.main.AocBench ^
         -w %WARMUP% -r %REPEATS% -a CERES -m BITSET_PACKED %%C >> "%LOG%" 2>&1
    if errorlevel 1 (
        echo   ECHEC sur %%~nxC
        goto :fail
    )
    >>"%LOG%" echo.
)

echo.
echo ============================================================
echo  [3/3] Recapitulatif
echo ============================================================
REM Les lignes CERES portent min/med/max, concepts et aretes : de quoi
REM comparer d'une campagne a l'autre sans ouvrir le fichier.
findstr /C:"CERES" "%LOG%"
echo.
echo   Profil complet : %LOG%
echo.
echo   Comparer avec la campagne precedente :
echo     fc /N "%LOG%" ^<ancien fichier^>
echo   Les COMPTEURS doivent etre identiques d'une campagne a l'autre :
echo   c'est le controle d'equivalence le plus sensible dont on dispose.
echo.
goto :end

:fail
echo.
echo ============================================================
echo  CAMPAGNE INTERROMPUE
echo ============================================================
endlocal
exit /b 1

:end
endlocal
exit /b 0