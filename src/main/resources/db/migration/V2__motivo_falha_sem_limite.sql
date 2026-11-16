-- O motivo da falha da saga pode trazer o erro de outro serviço (por exemplo, a resposta
-- do Mercado Pago num EstornoFalhou). Com VARCHAR(300), a gravação falhava, o evento ia
-- para a DLQ e a saga não chegava a FALHA, justamente o estado que dispara o alerta.
ALTER TABLE sagas ALTER COLUMN motivo_falha TYPE TEXT;
