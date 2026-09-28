param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$jdkRoot = Get-ChildItem "$projectRoot/.tooling/jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdkRoot) { $env:JAVA_HOME = $jdkRoot.FullName }
if (-not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) { throw 'Install JDK 17 and set JAVA_HOME.' }
New-Item -ItemType Directory -Force .tooling/test-classes | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d .tooling/test-classes app/src/main/java/cn/quietstart/ImportedRules.java app/src/main/java/cn/quietstart/Rules.java app/src/main/java/cn/quietstart/Subscription.java app/src/main/java/cn/quietstart/DnsPacket.java app/src/main/java/cn/quietstart/DnsProbe.java tests/CoreTest.java
if ($LASTEXITCODE -ne 0) { throw 'Core test compilation failed' }
& "$env:JAVA_HOME/bin/java.exe" -cp .tooling/test-classes cn.quietstart.CoreTest
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -d .tooling/test-classes app/src/main/java/cn/quietstart/SkipRules.java tests/SkipRulesTest.java
if ($LASTEXITCODE -ne 0) { throw 'Skip rule test compilation failed' }
& "$env:JAVA_HOME/bin/java.exe" -cp .tooling/test-classes cn.quietstart.SkipRulesTest
if ($LASTEXITCODE -ne 0) { throw 'Skip rule tests failed' }
if ($TestOnly) { return }
& "$projectRoot/gradlew.bat" --no-daemon assembleDebug lintDebug
if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed' }
New-Item -ItemType Directory -Force dist | Out-Null
Copy-Item app/build/outputs/apk/debug/app-debug.apk dist/QuietStart-0.6.0.apk -Force
Get-FileHash dist/QuietStart-0.6.0.apk -Algorithm SHA256
