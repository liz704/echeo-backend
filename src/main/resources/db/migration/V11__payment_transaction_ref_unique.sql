-- Références de transaction uniques (évite les doubles enregistrements en démo / futur webhook)
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_history_transaction_ref
    ON payment_history (transaction_ref)
    WHERE transaction_ref IS NOT NULL AND transaction_ref <> '';
