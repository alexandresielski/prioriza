$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$maven = (Get-Command mvn -ErrorAction Stop).Source
& $maven -B '-Dtest=GeradorDemonstracaoTeste' test
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar os exemplos.' }
Write-Host 'Planilhas ficticias geradas em target/demonstracao. Nao contem dados da operacao original.'
