ALTER TABLE atraso_cliente ADD COLUMN cliente_id TEXT REFERENCES local_atendimento(id);
CREATE INDEX indice_atraso_estabelecimento ON atraso_cliente(cliente_id, executado DESC);
CREATE UNIQUE INDEX atraso_cliente_sem_repeticao ON atraso_cliente(cliente_id, etapa, previsto, executado)
    WHERE cliente_id IS NOT NULL;
