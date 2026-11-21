CREATE TABLE importacao_base (
    tipo VARCHAR(50) PRIMARY KEY,
    nome_arquivo TEXT NOT NULL,
    conteudo BYTEA NOT NULL,
    sha256 CHAR(64) NOT NULL,
    quantidade_linhas INTEGER NOT NULL,
    importado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE local_atendimento (
    id TEXT PRIMARY KEY,
    nome TEXT NOT NULL,
    cidade TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    fonte VARCHAR(20) NOT NULL,
    detalhe TEXT NOT NULL,
    nomes_busca TEXT[] NOT NULL,
    cidade_busca TEXT NOT NULL,
    busca TEXT NOT NULL,
    CONSTRAINT latitude_valida CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT longitude_valida CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
CREATE INDEX indice_local_cidade ON local_atendimento(cidade_busca);
CREATE INDEX indice_local_nomes ON local_atendimento USING GIN(nomes_busca);

CREATE TABLE veiculo (
    placa TEXT PRIMARY KEY,
    frota TEXT,
    agrupamento TEXT,
    classificacao TEXT,
    pbt DOUBLE PRECISION,
    tara DOUBLE PRECISION,
    eixos DOUBLE PRECISION
);

-- As placas sem cadastro permanecem visiveis, mas nao sao aprovadas pelo motor.
CREATE TABLE posicao_veiculo (
    placa TEXT PRIMARY KEY,
    carreta TEXT,
    grupo TEXT NOT NULL,
    situacao TEXT NOT NULL,
    engatada BOOLEAN NOT NULL,
    cidade TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    ultima_posicao TIMESTAMP,
    origem_atual TEXT,
    remetente_atual TEXT,
    destino_atual TEXT,
    destinatario_atual TEXT
);

CREATE TABLE carga_historica (
    numero TEXT PRIMARY KEY,
    origem TEXT,
    destino TEXT,
    cidade_origem TEXT,
    cidade_destino TEXT,
    peso DOUBLE PRECISION,
    coleta TEXT,
    situacao TEXT,
    linhas INTEGER NOT NULL,
    documentos TEXT[] NOT NULL,
    revisar BOOLEAN NOT NULL,
    busca TEXT NOT NULL
);

CREATE TABLE viagem_historica (
    numero TEXT PRIMARY KEY,
    origem TEXT,
    destino TEXT,
    placa_tracao TEXT,
    placa_reboque TEXT,
    inicio_carga TIMESTAMP,
    fim_carga TIMESTAMP,
    inicio_descarga TIMESTAMP,
    fim_descarga TIMESTAMP,
    peso_original DOUBLE PRECISION,
    peso_adotado DOUBLE PRECISION,
    peso_assumido BOOLEAN NOT NULL,
    frete_total DOUBLE PRECISION,
    km_vazio DOUBLE PRECISION,
    km_carregado DOUBLE PRECISION,
    duracao_carga_valida BOOLEAN NOT NULL
);

CREATE TABLE simulacao (
    id UUID PRIMARY KEY,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    identificacao TEXT NOT NULL,
    carga JSONB NOT NULL,
    parametros JSONB NOT NULL,
    ajustes JSONB NOT NULL,
    resultado JSONB NOT NULL
);
