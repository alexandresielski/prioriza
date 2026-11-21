param([int]$Porta = 0, [switch]$Compilar, [switch]$Demonstracao)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Test-Path -LiteralPath '.env') {
    $configuracao = Get-Content -LiteralPath '.env' -Raw | ConvertFrom-StringData
    foreach ($nome in @('BANCO_URL','BANCO_USUARIO','BANCO_SENHA','PORTA_APLICACAO','PASTA_BASES')) {
        if ($configuracao.ContainsKey($nome)) { [Environment]::SetEnvironmentVariable($nome,$configuracao[$nome],'Process') }
    }
}
foreach ($nome in @('BANCO_URL','BANCO_USUARIO','BANCO_SENHA')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($nome))) {
        throw "Configure $nome no arquivo .env ou nas variaveis de ambiente. Consulte LEIA-ME.md."
    }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $java)) { throw 'Java nao encontrado. Configure JAVA_HOME para um JDK 21.' }
if ($Porta -eq 0) { $Porta = if ($env:PORTA_APLICACAO) { [int]$env:PORTA_APLICACAO } else { 8766 } }
if ($Porta -lt 1 -or $Porta -gt 65535) { throw 'Porta invalida.' }
if (Get-NetTCPConnection -LocalPort $Porta -State Listen -ErrorAction SilentlyContinue) { throw "A porta $Porta ja esta em uso. Escolha outra porta." }
$arquivo = Join-Path $PSScriptRoot 'target\prioriza-0.2.0.jar'
if ($Compilar -or -not (Test-Path -LiteralPath $arquivo)) {
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    & $maven -B package
    if ($LASTEXITCODE -ne 0) { throw 'A compilacao ou os testes falharam.' }
}
if ($Demonstracao) {
    $pasta = Join-Path $PSScriptRoot 'target\demonstracao\bases'
    if (-not (Test-Path -LiteralPath $pasta)) { throw 'Gere os exemplos com .\gerar-demonstracao.ps1.' }
    $env:PASTA_BASES = $pasta
}
Write-Host "Prioriza disponivel apos inicializacao em http://127.0.0.1:$Porta"
& $java -jar $arquivo "--server.port=$Porta"
exit $LASTEXITCODE
