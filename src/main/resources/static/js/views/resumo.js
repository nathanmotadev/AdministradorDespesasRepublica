// Resumo do mês: quanto saiu, quanto coube a mim, quanto paguei e para onde foi o dinheiro.
// Só entram as despesas em que a pessoa pagou ou tem parte (a mesma regra da lista de despesas).
import { api } from '../api.js';
import { h } from '../ui.js';
import { brl, deslocarMes, mesAtual, rotuloMes } from '../format.js';

const centavos = (valor) => Math.round(valor * 100);
let pedidoAtual = 0;

export function viewResumo(ctx) {
    const { state } = ctx;
    state.mesResumo ??= mesAtual();

    const rotulo = h('span', { class: 'rotulo', 'aria-live': 'polite' }, rotuloMes(state.mesResumo));
    const corpo = h('div', { class: 'stack resumo-corpo' }, h('p', { class: 'muted center' }, 'Carregando…'));

    function mudarMes(delta) {
        state.mesResumo = deslocarMes(state.mesResumo, delta);
        rotulo.textContent = rotuloMes(state.mesResumo);
        carregar();
    }

    async function carregar() {
        const pedido = ++pedidoAtual; // se a pessoa trocar de mês rápido, só vale a última resposta
        try {
            const dados = await api.resumoDoMes(state.casa.id, state.mesResumo);
            if (pedido !== pedidoAtual) return;
            corpo.replaceChildren(...conteudo(dados));
        } catch (erro) {
            if (pedido !== pedidoAtual) return;
            corpo.replaceChildren(h('div', { class: 'card vazio' },
                h('strong', {}, 'Não consegui carregar o resumo'), erro.message));
        }
    }

    carregar();
    return h('section', { class: 'pagina', 'aria-label': 'Resumo do mês' },
        h('div', { class: 'card-head' },
            h('div', { class: 'month-nav' },
                h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Mês anterior', onclick: () => mudarMes(-1) }, '‹'),
                rotulo,
                h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Próximo mês', onclick: () => mudarMes(1) }, '›'))),
        corpo);
}

function conteudo(dados) {
    if (dados.quantidade === 0) {
        return [h('div', { class: 'card vazio' },
            h('strong', {}, 'Nenhuma despesa sua neste mês'),
            'Aqui entram as despesas que você pagou ou em que tem uma parte.')];
    }
    return [
        cartaoTotal(dados),
        h('div', { class: 'resumo-grade' }, cartaoPorConta(dados.porConta), cartaoQuemPagou(dados.porPagador)),
        h('p', { class: 'resumo-nota muted' },
            'Conta só as despesas em que você pagou ou tem parte. Gastos dos outros moradores não aparecem aqui.'),
    ];
}

/** "8% a mais que setembro", "igual a setembro" ou, sem base de comparação, um aviso. */
export function comparacao(dados) {
    const anterior = rotuloMes(deslocarMes(dados.mes, -1)).split(' de ')[0];
    const antes = centavos(dados.totalMesAnterior);
    const agora = centavos(dados.total);
    if (antes === 0) return { tipo: 'neutro', texto: `sem despesas suas em ${anterior}` };
    const pct = Math.round(((agora - antes) / antes) * 100);
    if (pct === 0) return { tipo: 'neutro', texto: `igual a ${anterior}` };
    return pct > 0
        ? { tipo: 'mais', texto: `${pct}% a mais que ${anterior}` }
        : { tipo: 'menos', texto: `${Math.abs(pct)}% a menos que ${anterior}` };
}

function cartaoTotal(dados) {
    const comp = comparacao(dados);
    const [inteiro, centavosTexto] = brl(dados.total).replace('R$', '').trim().split(',');
    return h('section', { class: 'card resumo-total', 'aria-label': 'Total do mês' },
        h('p', { class: 'saldo-titulo' }, `Total do mês · ${dados.quantidade === 1 ? '1 despesa' : `${dados.quantidade} despesas`}`),
        h('p', { class: 'saldo-valor num' }, 'R$ ', h('strong', {}, `${inteiro},${centavosTexto}`)),
        h('span', { class: `resumo-pilula ${comp.tipo}` }, comp.texto),
        h('div', { class: 'saldo-grade' },
            h('div', { class: 'saldo-bloco receber' },
                h('span', { class: 'rotulo' }, 'Você pagou'), h('strong', { class: 'num' }, brl(dados.euPaguei))),
            h('div', { class: 'saldo-bloco deve' },
                h('span', { class: 'rotulo' }, 'Sua parte'), h('strong', { class: 'num' }, brl(dados.minhaParte)))));
}

function cartaoPorConta(itens) {
    const total = itens.reduce((soma, i) => soma + centavos(i.valor), 0) || 1;
    const percentual = (i) => Math.round((centavos(i.valor) / total) * 100);
    const cor = (i, indice) => (i.rotulo === 'Outras' && indice === itens.length - 1 && itens.length > 5
        ? 'var(--serie-outras)' : `var(--serie-${(indice % 5) + 1})`);
    return h('section', { class: 'card', 'aria-label': 'Para onde foi' },
        h('h2', {}, 'Para onde foi'),
        h('div', { class: 'resumo-barra', role: 'img',
            'aria-label': itens.map((i) => `${i.rotulo} ${percentual(i)}%`).join(', ') },
        itens.map((i, n) => h('span', { style: `flex:${centavos(i.valor)} 1 0;background:${cor(i, n)}` }))),
        h('ul', { class: 'resumo-lista' }, itens.map((i, n) => h('li', {},
            h('span', { class: 'ponto', 'aria-hidden': 'true', style: `background:${cor(i, n)}` }),
            h('span', { class: 'nome' }, i.rotulo),
            h('span', { class: 'pct muted num' }, `${percentual(i)}%`),
            h('strong', { class: 'num' }, brl(i.valor))))));
}

function cartaoQuemPagou(itens) {
    const maior = Math.max(...itens.map((i) => centavos(i.valor)), 1);
    return h('section', { class: 'card', 'aria-label': 'Quem pagou' },
        h('h2', {}, 'Quem pagou'),
        h('ul', { class: 'resumo-lista pagou' }, itens.map((i) => h('li', {},
            h('span', { class: 'nome' }, i.rotulo),
            h('strong', { class: 'num' }, brl(i.valor)),
            h('span', { class: 'progresso' }, h('span', { style: `width:${Math.round((centavos(i.valor) / maior) * 100)}%` }))))));
}
