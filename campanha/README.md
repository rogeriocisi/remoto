# Campanha de correção e modernização

Evidência da campanha descrita em `../../remoto-campanha-2026-11-29.md`. Um arquivo por teste,
com o critério declarado antes de rodar, o que foi medido, o veredito e **o que o teste não prova**.

- `fase1/testes_de_tabela.py` — testes de coerência sobre os 45 cenários, sem rodar simulação.
  Roda com `python campanha/fase1/testes_de_tabela.py` a partir da raiz do repositório.
- `fase1/*.json` — a evidência de cada teste.
- `fase1/RESUMO-*.md` — a leitura dos resultados, com a classificação de cada achado:
  defeito, divergência documentável, critério errado, ou pergunta para os autores.

`bin/` não é versionado: guarda a cópia do JAR usada na campanha, para que compilar não concorra
com simulações em curso (no Windows o build falha ao renomear um JAR aberto, e deixa no lugar um
artefato inválido).
