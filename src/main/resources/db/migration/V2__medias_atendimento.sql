CREATE TABLE media_atendimento (
    tipo TEXT NOT NULL CHECK (tipo IN ('carga','descarga')),
    cliente TEXT NOT NULL,
    cidade TEXT NOT NULL,
    cliente_busca TEXT NOT NULL,
    cidade_busca TEXT NOT NULL,
    total INTEGER NOT NULL,
    zeros INTEGER NOT NULL,
    invalidos INTEGER NOT NULL,
    extremos INTEGER NOT NULL,
    utilizados INTEGER NOT NULL,
    media_horas DOUBLE PRECISION,
    mediana_horas DOUBLE PRECISION,
    limite_inferior DOUBLE PRECISION,
    limite_superior DOUBLE PRECISION,
    PRIMARY KEY(tipo,cliente_busca,cidade_busca),
    CHECK (total = zeros + invalidos + extremos + utilizados)
);
ALTER TABLE importacao_base ADD COLUMN versao_tempos INTEGER NOT NULL DEFAULT 0;
