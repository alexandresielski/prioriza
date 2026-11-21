CREATE TABLE atraso_cliente (
 id UUID PRIMARY KEY, cliente TEXT NOT NULL, municipio TEXT NOT NULL, etapa TEXT NOT NULL CHECK(etapa IN ('CARGA','DESCARGA')),
 previsto TIMESTAMP NOT NULL, executado TIMESTAMP NOT NULL, observacao TEXT NOT NULL,
 registrado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE cenario_planejamento (
 id UUID PRIMARY KEY, criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 situacao TEXT NOT NULL CHECK(situacao IN ('SIMULADO','CONFIRMADO','CANCELADO')), bases TEXT NOT NULL,
 pedido JSONB NOT NULL, resultado JSONB NOT NULL
);
CREATE TABLE alocacao_planejada (
 id UUID PRIMARY KEY, cenario UUID NOT NULL REFERENCES cenario_planejamento(id),
 carga UUID NOT NULL REFERENCES carga_planejada(id), placa TEXT NOT NULL, carreta TEXT NOT NULL,
 ativa BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX unica_carga_alocada ON alocacao_planejada(carga) WHERE ativa;
CREATE UNIQUE INDEX unico_cavalo_alocado ON alocacao_planejada(placa) WHERE ativa;
CREATE UNIQUE INDEX unica_carreta_alocada ON alocacao_planejada(carreta) WHERE ativa;
