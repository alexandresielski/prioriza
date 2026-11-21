const fs=require('node:fs');const path=require('node:path');const {execFileSync}=require('node:child_process');
const raiz=path.resolve(__dirname,'..');
const candidatos=execFileSync(process.env.GIT_EXECUTAVEL||'git',['ls-files','--cached','--others','--exclude-standard','-z'],{cwd:raiz,encoding:'utf8'}).split('\0').filter(Boolean);
const erros=[];const revisados=new Set();
for(const nome of candidatos){
  if(revisados.has(nome))continue;revisados.add(nome);
  if(/^(\.banco|estado-local|Bases|legado-python|target|node_modules|verificacao|test-results|\.ferramentas)\//.test(nome)||/\.(dump|backup|bak|xlsx|xls|csv|zip|7z|rar|pem|key|p12|pfx|log)$/i.test(nome)||(/(^|\/)\.env($|\.)/.test(nome)&&nome!=='.env.example'))erros.push('Arquivo privado ou gerado: '+nome);
  if(nome.endsWith('.sql')&&!nome.startsWith('src/main/resources/db/migration/'))erros.push('SQL fora das migracoes: '+nome);
  const arquivo=path.join(raiz,nome);if(!fs.existsSync(arquivo)||!fs.statSync(arquivo).isFile())continue;
  if(!/\.(java|js|cjs|json|xml|properties|md|ps1|cmd|html|css|yaml|yml|txt)$/.test(nome)&&nome!=='.env.example')continue;
  const texto=fs.readFileSync(arquivo,'utf8');
  const caminhoPrivado=new RegExp('C:[/\\\\]'+'(?:Users|Projetos)'+'[/\\\\]','i');
  if(caminhoPrivado.test(texto))erros.push('Caminho absoluto de ambiente privado: '+nome);
  if(/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(texto))erros.push('Chave privada: '+nome);
  if(/^[ \t]*(?:spring\.datasource\.password|BANCO_SENHA)[ \t]*=[ \t]*[^\s$#].*$/m.test(texto))erros.push('Possivel credencial fixa: '+nome);
}
if(erros.length){console.error(erros.join('\n'));process.exit(1)}
console.log('Auditoria de publicacao aprovada: '+revisados.size+' arquivos candidatos, sem dados ou configuracoes privadas identificados pelos controles automaticos. Revise tambem documentos e imagens antes de futuras inclusoes.');
