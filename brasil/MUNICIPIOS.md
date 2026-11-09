# Consultas de municípios

A operação MUNICIPIOS antes retornava apenas totalMunicipios: não havia uma lista paginada a recuperar. Agora consulta projeções agregadas por município normalizado e UF no banco, sem carregar entidades. Cidades vazias são excluídas. Acentos, caixa e espaços seguem TextNormalization; nomes distintos e UFs distintas permanecem separados. O nome exibido é um valor cadastrado escolhido com MIN(TRIM(cidade)).

Parâmetros: estado, cidade, empresa, nome; pagina (inicia em zero), limite (1–50, padrão 20), todos (booleano) e ranking (booleano, quantidade decrescente com desempate por UF e município). Top dez: ranking=true, limite=10, todos=false.

todos=true inicia na primeira página e reúne as páginas automaticamente. O backend também ativa esse parâmetro para pedidos explícitos de listagem completa quando o modelo chama MUNICIPIOS. Os resultados integrais seguem na Function Response, preservando Function Calling e suas assinaturas. A apresentação de municípios é determinística, inclusive em falha posterior do Gemini.

Configurações Spring (também podem ser definidas por variáveis de ambiente):

```properties
agent.municipios.max-records=1000
agent.municipios.max-pages=50
```

O resultado contém registros (municipio, estado, quantidade), totalMunicipios, totalElements, totalPages, page, size, hasNext, completo, retornados, paginasConsultadas e totalInstalacoesRetornadas. Ao atingir um limite, informa que a lista é parcial e oferece continuação paginada com os mesmos filtros e ordenação. Não exige exportação para continuar. O total de instalações retornadas exclui instalações sem município válido.

AgentChatResponse mantém os campos existentes. Pedidos de gráfico ou tabela usam agora o contrato genérico versionado descrito em [VISUALIZACOES.md](VISUALIZACOES.md), com chartData e visualization, dados completos e explicação separada em response. Listagens textuais continuam disponíveis. O frontend deve identificar categorias pelo par municipio/estado. O endpoint de chat não mudou.

A memória preserva os resultados municipais completos enquanto couberem no limite app.agent.memory.max-chars existente; ultrapassado esse limite, a política existente pode omitir os dados históricos com aviso para consultar novamente. A resposta atual e chartData não são truncados por essa política.

Testes: AgentMunicipiosIntegrationTests cobre 0/20/21/98 registros, múltiplas páginas, estado, normalização, ranking, limites, soma das instalações, Function Response, texto, gráfico e persistência da memória. Usa H2 em modo PostgreSQL e Gemini simulado; não exige chamadas externas nem credenciais de produção.
