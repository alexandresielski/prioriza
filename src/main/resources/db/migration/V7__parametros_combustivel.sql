CREATE TABLE parametro_combustivel (
    id INTEGER PRIMARY KEY CHECK (id=1),
    preco_combustivel DOUBLE PRECISION,
    consumo_medio DOUBLE PRECISION,
    CHECK ((preco_combustivel IS NULL AND consumo_medio IS NULL) OR
      (preco_combustivel IS NOT NULL AND consumo_medio IS NOT NULL AND
       preco_combustivel>0 AND preco_combustivel<'Infinity'::double precision AND
       consumo_medio>0 AND consumo_medio<'Infinity'::double precision))
);
INSERT INTO parametro_combustivel(id) VALUES(1);
