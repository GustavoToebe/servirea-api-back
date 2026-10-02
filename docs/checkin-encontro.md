# Check-in por encontro — F15

Módulo `checkin/`, V079 (`checkin_sessao`, `checkin_registro`, ambas com RLS sem policy). Sem IA e sem localização: a prova de presença é conhecer um código aberto pela coordenação.

## Fluxo
1. A coordenação, com escala FINALIZADA, abre o check-in de uma celebração (POST /escalas/eventos/{id}/checkin `{minutos}`; 15 a 480, padrão 180, nunca além de 8 h depois do início; CHECKIN_GERENCIAR). A resposta traz o token **uma única vez**; no banco fica só o SHA-256. Abrir de novo revoga o código anterior; DELETE encerra.
2. A pessoa escalada, logada com conta vinculada explicitamente (PORTAL_VOLUNTARIO e plano), envia o código em POST /portal/checkin `{token}`. A pessoa vem do vínculo, nunca do corpo.
3. O servidor trava a escala, confere código ativo, celebração real de escala finalizada, janela (abre 1 h antes do início, horário de Brasília) e se a pessoa tem vaga naquela celebração. Grava um registro único por vaga e marca a presença como PRESENTE.

## Regras de segurança
- Token de 256 bits aleatórios, URL-safe, comparado por hash; código inválido, revogado, expirado, de outra paróquia ou de escala não finalizada responde o mesmo 404 genérico.
- Reutilização: um registro por vaga. Repetir devolve `jaRegistrado=true` sem duplicar nem reescrever a presença. O mesmo código serve a várias pessoas (é o código da sala), mas cada uma só se registra na própria vaga.
- Falta marcada pela coordenação (FALTOU) não é sobrescrita: 409 orientando procurar a coordenação. Presença manual continua em PATCH /escalas/vagas/{id}/presenca (VAGA_PRESENCA) e é a alternativa quando a pessoa não consegue usar o código.
- GET /escalas/eventos/{id}/checkin (CHECKIN) mostra se há código ativo, escalados, presentes e quem registrou pelo código (até 300).
- Auditoria: CHECKIN_ABRIR, CHECKIN_ENCERRAR e CHECKIN (por vaga).

## Limites
Sem QR code gerado pelo sistema (o link é copiado e pode ser transformado em QR por quem preferir), sem validação por proximidade, sem check-in de eventos gerais nem de dependentes por responsável, sem saída/horário de término. Perfis existentes precisam de CHECKIN e CHECKIN_GERENCIAR; ADMIN já tem o catálogo. Homologar com celulares reais antes de usar.
