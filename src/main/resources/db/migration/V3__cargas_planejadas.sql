CREATE TABLE carga_planejada (
    id UUID PRIMARY KEY,
    versao INTEGER NOT NULL DEFAULT 1,
    situacao TEXT NOT NULL DEFAULT 'PENDENTE' CHECK (situacao IN ('PENDENTE','CANCELADA')),
    identificacao TEXT NOT NULL,
    coleta TIMESTAMP NOT NULL,
    carga JSONB NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX indice_planejamento_coleta ON carga_planejada(situacao,coleta);
