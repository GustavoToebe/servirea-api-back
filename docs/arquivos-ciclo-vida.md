# Ciclo de vida durável dos arquivos — T13

Módulo `storage/`, V080 (`arquivo_pendencia`, com RLS sem policy). Cobre as fotos de voluntários, de eventos e da inscrição pública. Sem IA e sem varredura do bucket.

## Problema resolvido
Antes, o upload ao Supabase e a gravação no banco ocorriam dentro da mesma transação (com a paróquia travada durante o HTTP) e a remoção era "melhor esforço": falha do provedor ou queda da API deixava arquivo órfão sem rastro, e a foto substituída de um voluntário nunca era apagada.

## Desenho
- **Upload em três passos curtos** (`VoluntarioService.definirFoto`, `EventoService.enviarFoto`): (1) transação que trava a paróquia só para conferir cota e existência; (2) registro durável do UPLOAD em transação própria e envio ao provedor **fora de qualquer transação**; (3) transação que trava de novo, revalida a cota, grava a referência e valida. Falha no passo 3 descarta o arquivo na hora (`descartarUpload`); se o provedor também falhar, a pendência fica.
- **Remoção durável**: quando uma referência some (foto anterior substituída, foto de evento excluída), `agendarRemocao` grava REMOCAO **na transação do negócio**, ou seja, só existe se o negócio confirmar. Depois do commit tenta apagar na hora; sucesso (inclusive "já não existe") remove a pendência.
- **Job** (`ArquivoPendenciaJob`, a cada 5 minutos, `servire.storage.pendencias-ativo`, ligado por padrão): por paróquia, reserva até 10 pendências vencidas (reserva de 120 s por linha, seguro com várias réplicas), confere se o caminho ainda é usado por voluntário, inscrição ou foto de evento (se for, só encerra a pendência e **nunca apaga**), espera a carência de 6 h dos UPLOAD sem referência (a operação de negócio pode ainda estar em curso) e então remove. Falha aumenta `tentativas` e espera 10, 20, 40… minutos, no máximo 6 h, sem desistir.
- `StorageService.excluirConfirmando` informa sucesso real (o `excluir` antigo engole falhas). A implementação padrão delega a `excluir`.

## Efeito colateral aceito
Dois uploads concorrentes que juntos passariam da cota agora podem enviar os dois arquivos; o segundo é recusado com 409 COTA_EXCEDIDA e seu arquivo é descartado em seguida. A cota continua respeitada (teste de concorrência). Em troca, a trava da paróquia deixa de cobrir o HTTP.

## Limites
Na inscrição pública a foto também sobe fora da transação (nome do arquivo com UUID próprio, pré-validação de contatos, plano e cota antes do envio); falha ao gravar descarta o arquivo. Arquivos anteriores a V080 não são encontrados. Não há listagem do bucket para reconciliar o que está no armazenamento mas não no banco; isso exige uma operação separada, com lista paginada do provedor e janela de segurança. Pendência que falha para sempre não gera alerta ainda (consultar `arquivo_pendencia` por `tentativas` alto).
