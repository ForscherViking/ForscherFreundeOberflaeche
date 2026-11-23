@echo off
rem Startet den ForscherFreunde Hub mit der mitgelieferten JRE.
rem javaw = kein Konsolenfenster. Die JVM-Optionen halten Start schnell und Speicherbedarf klein:
rem   SerialGC + kleiner Heap: wenig RAM, keine zusaetzlichen GC-Threads auf schwachen CPUs
rem   TieredStopAtLevel=1: schnellerer Start, weniger CPU fuer den JIT-Compiler
rem Ein zweiter Start oeffnet KEINE zweite Instanz, sondern holt das offene Fenster nach vorne.
start "" "%~dp0jre\bin\javaw.exe" -XX:+UseSerialGC -Xms24m -Xmx192m -XX:TieredStopAtLevel=1 -Xss512k -jar "%~dp0ForscherFreundeHub.jar"
